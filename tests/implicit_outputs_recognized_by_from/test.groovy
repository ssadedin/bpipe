HG19=System.getenv("HG19")?:"/shared/hg19"
HGFA=System.getenv("HGFA")?:"$HG19/gatk.ucsc.hg19.fasta"
GATK=System.getenv("GATK")?:"/mnt/Bioinfo_Slow/simons/GenomeAnalysisTK-1.2-21-g6804ab6"

annotate = {
    from(["vcf","bam"]) {
        filter("anno") {
            exec """
                java -Xmx2g -jar $GATK/GenomeAnalysisTK.jar 
                   -R $HGFA
                   -T VariantAnnotator 
                   -I $input2
                   -o $output
                   -A QualByDepth
                   -BTI variant
                   --variant $input1
                   --dbsnp $HG19/dbsnp_132.hg19.vcf
            """
        }
    }
}

gatk_call_variants = {
  transform("vcf") {
      exec """
        java -Xmx5g -jar $GATK/GenomeAnalysisTK.jar -T UnifiedGenotyper 
            -R $HGFA
            -D $HG19/dbsnp_132.hg19.vcf
            -baq CALCULATE_AS_NECESSARY 
            -baqGOP 30 
            -nt 16
            -A DepthOfCoverage 
            -A AlleleBalance 
            -A HaplotypeScore 
            -A MappingQualityZero 
            -A QualByDepth 
            -A RMSMappingQuality 
            -A SpanningDeletions 
            -I $input1 
            -o $output 
            -metrics ${output}.metrics
        """
    }
}

variant_recalibration = {

    transform("csv") {
        exec """
             java -Xmx4g -jar $GATK/GenomeAnalysisTK.jar -T VariantRecalibrator 
               -R $HGFA
               -input $input1
               -resource:hapmap,known=false,training=true,truth=true,prior=15.0 $HG19/hapmap_3.3.hg19.sites.vcf
               -resource:omni,known=false,training=true,truth=false,prior=12.0 $HG19/1000G_omni2.5.hg19.sites.vcf
               -resource:dbsnp,known=true,training=false,truth=false,prior=8.0 $HG19/dbsnp_132.hg19.vcf
               -an QD -an HaplotypeScore -an MQRankSum -an ReadPosRankSum -an FS -an MQ 
               -recalFile $output
               -tranchesFile ${input}.tranches
               -rscriptFile plots.R
        """
    }
}

apply_calibration = {
//    from(["vcf","csv"]) {
        filter("recal") {
           exec """
            java -Xmx3g -jar $GATK/GenomeAnalysisTK.jar 
                 -T ApplyRecalibration 
                 -R $HGFA
                 -input $input.vcf
                 --ts_filter_level 99.0 
                 -tranchesFile *.tranches
                 -recalFile $input.csv
                 -o $output
            """
        }
 //   }
}

Bpipe.run {
    // align_bowtie + sort_to_bam + remove_duplicates + reorder + index_bam + count_covariates + recalibrate_bam + local_realign + call_variants + filter_variants_by_depth + annotate_variants
    // count_covariates + recalibrate_bam + local_realign + call_variants + filter_variants_by_depth + annotate_variants + count_variants + compute_variant_set
    // add_default_read_group + annotate + variant_recalibration
    gatk_call_variants + variant_recalibration + apply_calibration
}
