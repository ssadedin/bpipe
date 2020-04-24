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

import java.util.Map
import java.util.regex.Pattern

import org.gitlab4j.api.GitLabApi
import org.gitlab4j.api.IssuesApi
import org.gitlab4j.api.NotesApi

import groovy.json.JsonSlurper
import groovy.text.Template
import groovy.util.logging.Log
import org.gitlab4j.api.models.*

@Log
class GitlabNotificationChannel implements NotificationChannel {
    
    ConfigObject cfg
    
	GitLabApi gitlab 
    
	Project project
	
	String baseURL
    
    public GitlabNotificationChannel(ConfigObject cfg) {
        this.cfg = cfg
        log.info "Connecting to gitlab at $cfg.url using token"
        gitlab = new GitLabApi(cfg.url, cfg.token)
        
		log.info "Notification channel $cfg.name connected to gitlab successfully"
        project = gitlab.projectApi.getProjects(cfg.project).find { Project p -> p.name == cfg.project }
        
		log.info "Found project $project.name from Gitlab at $cfg.url"
		this.baseURL = "$cfg.url/api/v4"
    }

	@Override
	public void notify(PipelineEvent event, String subject, Template template, Map<String, Object> model) {
        
        Map issueDetails = model['send.content']
		
        if(this.updateExistingIssue(issueDetails))
            return
		
		Map params = [
			title: issueDetails.title,
		]
		
		if('description' in issueDetails)
			params.description = issueDetails.description
		
			
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
             
            issueId = searchResult[0].iid
            log.info "Found issue $issueId corresonding to title $issueDetails.title in project $project.id"
            
    		NotesApi notesApi = gitlab.notesApi
    		notesApi.createIssueNote(project.id, issueId , issueDetails.description)
            return true
		}
		catch(PipelineError e) {
			log.info "No issue found corresonding to title $issueDetails.title in project $project.id: treating as new issue"
            return false
		}
    }

	@Override
	public String getDefaultTemplate(String contentType) {
		return null;
	}

}
