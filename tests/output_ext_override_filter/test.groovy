/**
 * This is testing the case where the output expected from the filter 
 * (in this case, test.over.txt) conflicts with the output actually referenced
 * by the user. In that case we should go with what the user said, as long
 * as there was actualy an alternative input that could have created 
 * the output they expected. This is needed because input order can vary
 * in a pipeline so it makes it hard if the user can't declare it like this
 */
hello = {
  filter("over") {
    exec """
      cp $input $output.csv
    """
  }
}

run { hello }
