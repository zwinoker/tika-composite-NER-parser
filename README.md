# Composite NER Agreement Parser
A parser for Apache Tika 1.12 that performs NER using CoreNLP, OpenNLP, NLTK, and grobid-quantities. Takes text input (eg from Text-Tag-Ratio output). 

# Installation
1. Install the Python NLTK REST server ```http://wiki.apache.org/tika/TikaAndNLTK```
2. Install the grobid-quantities REST server ```https://github.com/kermitt2/grobid-quantities```
3. Install Apache OpenNLP and CoreNLP 	```https://wiki.apache.org/tika/TikaAndNER```
4. Copy CoreNLPNERecogniser.java to ```PATH-TO-TIKA-1.12/tika-parsers/src/.../ner/corenlp/corenlpnerecogniser.java```
5. Copy OpenNLPNameFinder.java to ```PATH-TO-TIKA-1.12/tika-parsers/src/.../ner/opennlp/OpenNLPNameFinder.java```
6. Copy QuantityRestService.java to ```PATH-TO-GROBID/grobid-quantities/src/.../service/QuantityRestService.java```
7. Recompile grobid-quantities
8. Copy CompositeNERAgreementParser.java to ```PATH-TO-TIKA-1.12/tika-parsers/src/.../ner/```
9. Recompile Tika to use the updated NER parsers

# Usage
The parser accepts text data, so extract relevant text from any document and give it to this parser. Each NER service is then called and the results are combined by taking their union. If a named entity exists in more than one result, then the result with the highest frequency is used. This is then written to the input file's metadata as a JSON entry with the label "max joint agreement entities". Each individual NER toolkit's result is also written to the metadata for comparison with the joint result. Measurements in the data are found using the Grobid-Quantities REST server and written as-is to the input file's metadata with the label "quantities". Before running the parser, start the NLTK server using ```nltk-server -v --port 8888```. Start the GQ server by navigating to ```PATH-TO-GROBID/grobid-quantities/``` and using ```mvn -Dmaven.test.skip=true jetty:run-war```. 