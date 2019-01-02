

hello = {

   filter('pass') {
       exec """
            cat $input.vcf > $output.vcf
        """
   }
}

run {
   hello 
}




