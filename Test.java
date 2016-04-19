// Use to test CompositeNERAgreementParser

import java.io.*;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ToXMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.apache.tika.sax.XHTMLContentHandler;



public class Test {

	public static void main(String [] args){

		String fileName = "testdata";
		Parser parser = new CompositeNERAgreementParser();
		ContentHandler handler = new BodyContentHandler(new ToXMLContentHandler());
		Metadata metadata = new Metadata();
		ParseContext context = new ParseContext(); 
		
		try(InputStream stream = new FileInputStream(fileName)){
			metadata.set("source-file-path", fileName);
		
			parser.parse(stream, handler, metadata, context);
			
			System.out.println(metadata.toString());
			
			stream.close();
		}

		catch(Exception e) {
            System.out.println(e.getMessage());                
        }


	}



}