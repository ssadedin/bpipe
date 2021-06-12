/*
 * Copyright (c) Murdoch Childrens Research Institute and Contributers
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package bpipe

import java.util.regex.Pattern

import org.gitlab4j.api.GitLabApi
import org.gitlab4j.api.IssuesApi
import org.gitlab4j.api.NotesApi

import groovy.json.JsonSlurper
import groovy.text.Template
import groovy.transform.CompileStatic
import groovy.util.logging.Log
import org.gitlab4j.api.models.*

@CompileStatic
class GitlabFileReference {
    FileUpload upload
    String path
    
    String replaceIn(String content) {
        content.replaceAll(/\[(.*)\]\($path\)/, '[$1](' + upload.url + ')')
    }
}

@CompileStatic
class NonuniqueGitlabIssue extends FatalMessagingError {

    public NonuniqueGitlabIssue(String msg) {
        super(msg);
    }
}

@Log
class GitlabNotificationChannel implements NotificationChannel {
    
    Map cfg
    
	GitLabApi gitlab 
    
	Project project
	
	String baseURL
    
    public GitlabNotificationChannel(Map cfg) {
        this.cfg = cfg
        log.info "Connecting to gitlab at $cfg.url using token"
        gitlab = new GitLabApi(cfg.url, cfg.token)
        
		log.info "Notification channel $cfg.name connected to gitlab successfully"

        if(cfg.project instanceof Number) {
            project = gitlab.projectApi.getProject((int)cfg.project)
        }
        else
        if(cfg.project instanceof String) {
            project = gitlab.projectApi.projects.find { p -> 
                   p.name == cfg.project 
            }            
        }
        else
            throw new IllegalArgumentException("Gitlab project must be specified by either string (name) or id (integer) but you provided a value of type ${cfg.project.class.name}")
        
		log.info "Found project $project.name from Gitlab at $cfg.url"

		this.baseURL = "$cfg.url/api/v4"
    }

	@Override
	public void notify(PipelineEvent event, String subject, Template template, Map<String, Object> model) {
        
        Map issueDetails = model['send.content']
       
       	if(!('description' in issueDetails) && template) {
			issueDetails.description = template.make(issueDetails + model).toString()
        }

        List<GitlabFileReference> fileReferences = []
        if(model['send.file']) {
            fileReferences <<  uploadFile(model['send.file'])
        }
        
        if(issueDetails.description) {
            fileReferences.each { ref ->
                issueDetails.description = ref.replaceIn(issueDetails.description)
            }
        }
 	
        if(this.updateExistingIssue(issueDetails))
            return
            
        if(issueDetails.required)
            throw new FatalMessagingError("Unable to locate required issue matching $issueDetails.title on channel $cfg.name")
            
        if(issueDetails.title instanceof Map) {
            log.info "Could not identify issue matching $issueDetails.title: issue will not be updated"
            return
        }
		
		Map params = [
			title: issueDetails.title,
            description: issueDetails.description
		]
			
		if('assignee' in issueDetails)  {
			User u = gitlab.userApi.findUsers(issueDetails.assignee)[0]
			params.assignee_ids = u.id	
		}
        else
        if('assignee' in cfg) {
			User u = gitlab.userApi.findUsers(issueDetails.assignee)[0]
			params.assignee_ids = u.id	            
        }
			
	    if('label' in issueDetails)
			params.labels=issueDetails.label
       
		Utils.sendURL(null, 'POST', "$baseURL/projects/$project.id/issues", ["PRIVATE-TOKEN": cfg.token], params)
	}
    
    boolean updateExistingIssue(Map issueDetails) {
        
        IssuesApi issuesApi = gitlab.issuesApi
        
        Map<String,String> params = [
            scope: 'all',
            state: 'opened',
        ]
        
        String titleMatch = null
        if(issueDetails.title instanceof Map) {
            params.search = issueDetails.title.search
            titleMatch = issueDetails.title.match
        }
        else {
            params.search = issueDetails.title
        }
        
		// Search the project issues for one matching the given title
		Integer issueId = null
		try {
            
			String searchResultJSON = 
                Utils.sendURL(params, 'GET', "$baseURL/projects/$project.id/issues", ["PRIVATE-TOKEN": cfg.token])
			
			List<Map> searchResult = new JsonSlurper().parseText(searchResultJSON)
           
            if(titleMatch) {
                log.info "Prior to filtering found ${searchResult.size()} issues"
                Pattern pattern = ~titleMatch
                searchResult = searchResult.grep { Map issueJSON ->
                    issueJSON.title =~ pattern 
                }
            }

            log.info "Final search result contains ${searchResult.size()} issues"
			if(searchResult.isEmpty()) {
                return false
			}
            
            if(searchResult.size()>1 && issueDetails.unique) 
                throw new NonuniqueGitlabIssue("Search for $issueDetails.title in project $project.id yielded multiple results when configured with a unique constraint")
             
            issueId = searchResult[0].iid
            log.info "Found issue $issueId corresonding to title $issueDetails.title in project $project.id"
            
    		NotesApi notesApi = gitlab.notesApi
    		notesApi.createIssueNote(project.id, issueId , issueDetails.description)
            return true
		}
        catch(NonuniqueGitlabIssue ngi) {
            throw ngi
        }
		catch(PipelineError e) {
			log.info "No issue found corresonding to title $issueDetails.title in project $project.id: treating as new issue"
            return false
		}
    }
    
    @CompileStatic
    GitlabFileReference uploadFile(def fileLikeObj) {
        File actualFile = new File(fileLikeObj.toString()).absoluteFile
        
        log.info "Uploading $actualFile to project $project.id at $baseURL"
        
        def result = gitlab.projectApi.uploadFile(project.id, actualFile)
        
        log.info "File upload suceeded with path: " + result.url

        return new GitlabFileReference(path: fileLikeObj.toString(), upload: result)
    }

	@Override
	public String getDefaultTemplate(String contentType) {
		return null;
	}

}
