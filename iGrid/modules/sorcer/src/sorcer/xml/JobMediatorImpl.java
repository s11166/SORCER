/*
 * Copyright 2010 the original author or authors.
 * Copyright 2010 SorcerSoft.org.
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sorcer.xml;

// JAXP APIs
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.DocumentBuilder;

// exceptions
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

// file handling
import java.io.File;
import java.io.IOException;

// DOM related
import org.w3c.dom.*;

// xml document
import com.sun.xml.tree.XmlDocument;

// POA
import org.omg.PortableServer.*;

// sorcer related
import java.io.*;
import java.util.*;
import java.rmi.*;
import jgapp.util.*;
import sorcer.service.*;
import sorcer.service.util.*;
import sorcer.util.*;


public class JobMediatorImpl extends sorcer.service.xml.JobMediatorPOA {
    
    ServiceJobXMLDomParser parser = new ServiceJobXMLDomParser();

    /**
     * Execute the SORCER job that is described by the input string. 
     *
     * @param jobXML: the XML definition of a SORCER job
     */
    public void executeJob (java.lang.String jobXML) {

	DocumentBuilderFactory factory = DocumentBuilderFactory.initInstance();
	
	try {
	    // display input
	    System.err.println("Got: \n" + jobXML);

	    // parse xml
	    DocumentBuilder builder = factory.newDocumentBuilder();
	    InputStream istream = new ByteArrayInputStream(jobXML.getBytes());
	    org.w3c.dom.Document document = builder.parse(istream);

	    // print
	    XmlDocument xdoc = (XmlDocument)document;
	    xdoc.write(System.out);

	    // traverse and print
	    ServiceJobXMLDomParser p = new ServiceJobXMLDomParser();
	    p.traverse(document);

	    // construct a job
	    Job job = p.constructServiceJob(document);
	    System.err.println("Job: \n" + job);
	    System.err.flush();
	    
	    // print job
	    prettyPrintJob(job);

	    // run job
	    //JobExecutor exec = ServiceProviderAccessor.getJobExecutor();
	    String server = Sorcer.getRMILocation() + 
		Sorcer.getProperty("sorcer.env.jobberServer");
	    System.err.println(server);

	    JobExecutor jobExec = (JobExecutor)Naming.lookup(server);
	    System.err.println("Got JobExecutor: " + jobExec);

	    Job outputJob = jobExec.executeJob(job);
	    System.err.println("Job has been submitted to SORCER!!");

	    // print output job
	    prettyPrintJob(outputJob);
	    

        } catch (SAXParseException spe) {
           // Error generated by the parser
           System.out.println ("\n** Parsing error" 
              + ", line " + spe.getLineNumber ()
              + ", uri " + spe.getSystemId ());
           System.out.println("   " + spe.getMessage() );

           // Use the contained exception, if any
           Exception  x = spe;
           if (spe.getException() != null)
               x = spe.getException();
           x.printStackTrace();

        } catch (SAXException sxe) {
           // Error generated by this application
           // (or a parser-initialization error)
           Exception  x = sxe;
           if (sxe.getException() != null)
               x = sxe.getException();
           x.printStackTrace();

        } catch (ParserConfigurationException pce) {
            // Parser with specified options can't be built
            pce.printStackTrace();

        } catch (IOException ioe) {
	    // I/O error
	    ioe.printStackTrace();
        } catch (Exception exc) {
	    exc.printStackTrace();
	}
    }

    public void prettyPrintJob(Job job) {
	    Enumeration e = job.elements();
	    while (e.hasMoreElements()) {
		ServiceExertion task = (ServiceExertion)e.nextElement();
		System.err.println("task: " + task);
		System.err.println("  taskID: " + task.taskID);
		System.err.println("  name: " + task.name);
		System.err.println("  description: " + task.description);
		System.err.println("  status: " + task.status);
		System.err.println("  priority: " + task.priority);
		System.err.println("  method: " + task.method);
		System.err.println("  provider: " + task.method.providerName);
		System.err.println("  service: " + task.method.serviceType);
		System.err.println("  contexts: " + 
				   jgapp.util.Util.arrayToRequest(task.contexts));
	    }
    }

    public static void main(String [] args) {
    try {
	//Initialize the ORB.
	org.omg.CORBA.ORB orb =org.omg.CORBA.ORB.init(args,null);
	
	//get a reference to the root POA
	POA rootPOA =POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
	
	//Create policies for our persistent POA
	org.omg.CORBA.Policy [] policies = {
	    rootPOA.create_lifespan_policy(LifespanPolicyValue.PERSISTENT)
	};

	//Create myPOA with the right policies
	POA myPOA =rootPOA.create_POA("JobMediator_poa",
				      rootPOA.the_POAManager(),
				      policies );

	// set up Security manager
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new RMISecurityManager());
	}

	//Create the servant
	JobMediatorImpl mediatorServant = new JobMediatorImpl();

	//Decide on the ID for the servant
	byte [] mediatorID = "JobMediator".getBytes();

	//Activate the servant with the ID on myPOA
	myPOA.activate_object_with_id(mediatorID, mediatorServant);

	//Activate the POA manager
	rootPOA.the_POAManager().activate();
	System.err.println(myPOA.servant_to_reference(mediatorServant)+"is ready.");

	//Wait for incoming requests
	orb.run();

    }catch (Exception e){
	e.printStackTrace();
    }
}
}
