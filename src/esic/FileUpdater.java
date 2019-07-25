package esic;
//Copyright 2018 ESIC at WSU distributed under the MIT license. Please see LICENSE file for further info.

public class FileUpdater
{
	protected static void updateCSVFileWithRemoteVulnDBData (String inputFileName, String credentialsFileName, AHAGUI parentGUI, AHAController controller, int verbosity)
	{
		String auth="";
		int progress=0;
		javax.swing.ProgressMonitor pm=null;
		AHAGUIHelpers.tryCancelSplashScreen();
		try 
		{ 
			String message="                                                                "; //the giant blank string helps us guarantee a reasonable minimum size
			pm=new javax.swing.ProgressMonitor(parentGUI,message,"Preparing to start updating file...",progress++,5); 
			pm.setMillisToDecideToPopup(1);
			pm.setMillisToPopup(1); 
		} catch (Exception e) {} //in case we're running headlessly
		AHAGUIHelpers.tryUpdateProgress(pm,progress++,-1,"Opening input file ...");
		java.util.TreeMap<String,Integer> hdr=new java.util.TreeMap<>();
		java.util.TreeMap<String,String[]> binaries=new java.util.TreeMap<>();
		java.util.ArrayList<java.util.ArrayList<String>> inputLines=new java.util.ArrayList<>(512);
		java.util.TreeMap<String, String> metadata=new java.util.TreeMap<>();
		metadata.put("RequiredHeaderTokens", "processname,pid,protocol,state,localport,localaddress,remoteaddress,remoteport,remotehostname,sumsha1,sumsha256,sumsha512,summd5");
		System.out.printf("Updating file. inputFile=%s credentialsFile=%s\n", inputFileName, credentialsFileName);
		try
		{
			byte[] tempCreds=java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(credentialsFileName));
			if (tempCreds.length<6) { System.out.println("Failed to open credentials file, bailing."); return; }
			auth= new String(tempCreds,"UTF8").trim();
			if (!AHAModel.readCSVFileIntoArrayList(inputFileName, metadata,hdr, inputLines)) { System.out.println("Failed to read input file, bailing."); return; }
		} catch (Exception e) { e.printStackTrace(); } 
		AHAGUIHelpers.tryUpdateProgress(pm,progress++,40,"Processing input file...");
		
		int lineNumber=1;
		for (java.util.ArrayList<String> tokens: inputLines)
		{
			try
			{
				String procName=tokens.get(hdr.get("processname"));
				if ( procName!=null && !procName.equals("") && binaries.get(procName)==null )
				{
					String[] fileSums=new String[5]; //TODO convert into a map?
					if (hdr.get("sumsha512")!=null) { fileSums[0]=tokens.get(hdr.get("sumsha512")); }
					if (hdr.get("sumsha256")!=null) { fileSums[1]=tokens.get(hdr.get("sumsha256")); }
					if (hdr.get("sumsha1")!=null) { fileSums[2]=tokens.get(hdr.get("sumsha1")); }
					if (hdr.get("summd5")!=null) { fileSums[3]=tokens.get(hdr.get("summd5")); }
					
					boolean hasHashes=false;
					for (int i=0;i<4;i++)
					{
						if ( fileSums[i]!=null && !fileSums[i].equals("") && !fileSums[i].equals("scanerror") ) { hasHashes=true; }
					}
					if ( hasHashes ) { binaries.put(procName,fileSums); } 
					lineNumber++;
				}
			} catch (Exception e) { System.out.print("Exception processing line number "+lineNumber+":"); e.printStackTrace(); } 
		}
		
		if (binaries.isEmpty())
		{
			System.err.println("!!! No hashes found in provided BinaryAnalysis.csv to update, aborting. !!!");
			try { if (pm!=null) { pm.close(); } } catch (Exception e) {}
			if (parentGUI!=null) { javax.swing.JOptionPane.showMessageDialog(parentGUI, "No hashes found in provided BinaryAnalysis.csv to use for updating, aborting.\nPlease use a file from a newer version of the scraper.", "Unable to update file", javax.swing.JOptionPane.WARNING_MESSAGE); }
			return;
		}
		
		AHAGUIHelpers.tryUpdateProgress(pm,progress++,5+binaries.size(),"Requesting information from remote sources...");
		
		String vers="AHA-GUI";
		try { vers=AHAGUI.class.getPackage().getImplementationVersion().split(" B")[0].replaceAll(" ", "/"); } catch (Exception e) {}
		
		java.util.TreeMap<String,String> connectionProperties=new java.util.TreeMap<>();
		connectionProperties.put("Host", "microapi.adolus.com");
		connectionProperties.put("Accept", "*/*");
		connectionProperties.put("User-Agent", vers);
		connectionProperties.put("x-api-key", auth);
		String baseUrlString="https://microapi.adolus.com/files/";
		
		java.util.TreeMap<String,java.util.TreeMap<String,String>> aDolusResults=new java.util.TreeMap<>();
		System.out.println("Input file read, prepairing to check internet database for matches...");
		boolean userWasWarnedAboutCreds=false;
		for ( String fileName : binaries.keySet() )
		{
			AHAGUIHelpers.tryUpdateProgress(pm,progress++,-1,"Requesting info on "+fileName);
			if (AHAGUIHelpers.tryGetProgressCanceled(pm)) { System.err.println("Update process exiting on user cancel."); return; }
			String[] data=binaries.get(fileName); //System.out.printf("Got binary=%s sha512=%s 256=%s\n", s, data[0], data[1]);
			try
			{
				javax.net.ssl.HttpsURLConnection httpsConnection = openHttpConnection(baseUrlString+"details/"+data[0],connectionProperties); //java.security.cert.Certificate[] certs=httpsConnection.getServerCertificates(); //for(java.security.cert.Certificate cert : certs) {}
			
				System.out.printf("Checking '%s' [%s]. HTTP%s\n",fileName,data[0],httpsConnection.getResponseCode());
				java.util.TreeMap<String, String> details=null, scoreDetails=null;
				int firstResponseCode=httpsConnection.getResponseCode();
				if ( firstResponseCode == 200)
				{
					details=JSONSteamRoller.pave(getResultFromWebCall(httpsConnection));
					
					httpsConnection=openHttpConnection(baseUrlString+"scoreDetails/"+data[0],connectionProperties);
					if (httpsConnection.getResponseCode() == 200)
					{
						scoreDetails=JSONSteamRoller.pave(getResultFromWebCall(httpsConnection));
					}
				}
				else if ( firstResponseCode==401 && !userWasWarnedAboutCreds )
				{
					userWasWarnedAboutCreds=true;
					System.err.println("!!! Check that your credentials.txt file has current credentials in it! We will continue trying these creds without additional warnings !!!");
					if (parentGUI!=null) { javax.swing.JOptionPane.showMessageDialog(null,  "Check that your credentials.txt file has current credentials in it!\n We will continue trying these creds without additional warnings", "Check Credentails", javax.swing.JOptionPane.WARNING_MESSAGE); }
					continue; //use "null" as parent for JOptionPane otherwise it will try to show under the progress manager, which is annoying
				}
				else {} //TODO: do something else for other resp codes?
				
				if (details!=null && verbosity>4)
				{
					System.out.println("--- Details ---");
					for (java.util.Map.Entry<String, String> entry : details.entrySet()) { System.out.printf("%s=%s\n", entry.getKey(), entry.getValue()); }
				}
				if (verbosity>0)
				{
					if (scoreDetails!=null)
					{
						System.out.println("\n--- ScoreDetails --- ");
						for (java.util.Map.Entry<String, String> entry : scoreDetails.entrySet()) { System.out.printf("%s=%s\n", entry.getKey(), entry.getValue()); }
					}
					if (details!=null || scoreDetails!=null) { System.out.printf("--- --- --- --- --- end results for file='%s' --- --- --- --- ---\n", fileName); }
				}
				if (scoreDetails==null) { continue; } //we didn't find out anything
				
				java.util.TreeMap<String,String> aDolusResultRecord=new java.util.TreeMap<>();
				if (data[0]!=null && data[0].length()>10) //minimal check to make sure we had a hash, then write results for this binary into the internal storage list
				{
					aDolusResultRecord.put("filename", fileName);
					aDolusResultRecord.put("hash", data[0]);
					String adolusScore=scoreDetails.get("score");
					if (adolusScore==null) { adolusScore=""; }
					aDolusResultRecord.put("adolusscore", adolusScore);
					String aDolusSource=scoreDetails.get("sourceReputation.sourceStatus");
					if (aDolusSource==null) { aDolusSource=""; }
					aDolusResultRecord.put("adolusdatasource", aDolusSource);
					String adolusKnownMalware=scoreDetails.get("malware");
					if (adolusKnownMalware==null) { adolusKnownMalware=""; }
					aDolusResultRecord.put("adolusknownmalware", adolusKnownMalware);
					String adolusCVEs="";
					int numCVE=0;
					double totalCVEScore=0, worstCVEScore=0;
					for (int i=0;i<100;i++)
					{
						String s=scoreDetails.get("cve.items["+i+"].id");
						if (s!=null) 
						{ 	
							if (!adolusCVEs.equals("")) { adolusCVEs+=", "; } 
							adolusCVEs+=s;
							numCVE++;
							String cveScore=scoreDetails.get("cve.items["+i+"].score");
							try { 
								if ( cveScore!=null ) 
								{ 
									double dCveScore=Double.parseDouble(cveScore);
									totalCVEScore+=dCveScore; 
									if ( dCveScore > worstCVEScore) { worstCVEScore=dCveScore; }
								}
							}
							catch (Exception e) { System.out.println("Failed to parse cve score as double. input='"+cveScore+"'"); }
						}
					}
					//System.out.println("Got CVE Score total avg="+Double.toString(totalCVEScore/numCVE)+" numCVE="+numCVE+" total="+totalCVEScore);
					if (numCVE > 0) { aDolusResultRecord.put("adoluscvescore", Double.toString(totalCVEScore/numCVE)); }
					else { aDolusResultRecord.put("adoluscvescore", "0"); }
					aDolusResultRecord.put("adoluscves", adolusCVEs);
					aDolusResultRecord.put("adolusnumcves", Integer.toString(numCVE));
					aDolusResultRecord.put("adolusworstcvescore", Double.toString(worstCVEScore));
					aDolusResults.put(data[0], aDolusResultRecord);
					System.out.println("Found information on file="+fileName+" will update records.");
				}
			}
			catch (Exception e) { e.printStackTrace(); }
		}
		System.err.println("Got "+aDolusResults.size()+" result(s) from aDolus, updating file.");
		AHAGUIHelpers.tryUpdateProgress(pm,progress++,-1,"Writing output...");
		String updateTimestamp=Long.toString(System.currentTimeMillis());
		
		String originalHeaderLine=metadata.get("OriginalHeaderLine");
		int numberOfColumnsAdded=0;
		String[] newColumns={"aDolusScore","aDolusCVEs","aDolusCVEScore","aDolusNumCVEs","aDolusWorstCVEScore","aDolusDataSource","aDolusTimeChecked"}; //new column names 
		for (String col:newColumns)
		{
			if (hdr.get(col.toLowerCase())==null) //check if this input file already had any of the columns
			{
				hdr.put(col.toLowerCase(), hdr.size());  //if it did not, add new column 
				originalHeaderLine+=",\""+col+"\"";
				numberOfColumnsAdded++;
			}
		}
		
		try
		{
			java.nio.file.Path source=java.nio.file.Paths.get(inputFileName), dest=java.nio.file.Paths.get(inputFileName+".bak");
			java.nio.file.Files.copy(source, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) { System.err.println("Failed to copy backup file before updating (sorry):"); e.printStackTrace(); }
		
		try (java.io.FileWriter outputFile=new java.io.FileWriter(inputFileName, false))
		{
			String[] hashTypes= {"sumsha512","sumsha256","sumsha1","summd5"};
			java.util.TreeMap<String,String> matchingResult=null;
			
			outputFile.write(originalHeaderLine+"\n");
			for (java.util.ArrayList<String> inputLine : inputLines)
			{
				for (int i=0;i<numberOfColumnsAdded;i++) { inputLine.add(""); } //add new columns to internal data representation if needed to make output easier 
				for (String colName : hashTypes)
				{
					try
					{
						Integer column=hdr.get(colName);
						String hash=inputLine.get(column);
						matchingResult=aDolusResults.get(hash);
						if (matchingResult!=null) { break; }
					} catch (Exception e) { e.printStackTrace(); }
				}
				if (matchingResult!=null) //add data we got from aDolus to the line
				{
					try
					{
						inputLine.set(hdr.get("adolusscore"), matchingResult.get("adolusscore"));
						inputLine.set(hdr.get("adoluscves"), matchingResult.get("adoluscves"));
						inputLine.set(hdr.get("adoluscvescore"), matchingResult.get("adoluscvescore"));
						inputLine.set(hdr.get("adolusnumcves"), matchingResult.get("adolusnumcves"));
						inputLine.set(hdr.get("adolusworstcvescore"), matchingResult.get("adolusworstcvescore"));
						inputLine.set(hdr.get("adolusdatasource"), matchingResult.get("adolusdatasource"));
						inputLine.set(hdr.get("adolustimechecked"), updateTimestamp);
					} catch (Exception e) { e.printStackTrace(); }
				}
				StringBuilder outputLine=new StringBuilder("");
				for (int j=0;j<inputLine.size();j++) //write the line out
				{
					outputLine.append("\"");
					outputLine.append(inputLine.get(j));
					outputLine.append("\",");
				}
				outputLine.replace(outputLine.length()-1, outputLine.length(), "\n");
				outputFile.write(outputLine.toString());
			}
		} catch (Exception e) { System.out.println("Exception while writing output file...probably not a good sign. Exception:"); e.printStackTrace();  }
		
		System.out.println("Task complete.");
		AHAGUIHelpers.tryUpdateProgress(pm,progress,progress,"Task complete.");
		if (controller!=null) { controller.openfileOrReload(true); /*.terminateGUI(true, false);*/ } //if we got launched from the gui, this will termiante the current gui and reload the updated file
	}
	
	private static String getResultFromWebCall ( javax.net.ssl.HttpsURLConnection httpsConnection )
	{
		String rawJson=""; //System.out.println("Cipher Suite : " + httpsConnection.getCipherSuite());
		try
		{
			java.io.BufferedReader br=new java.io.BufferedReader(new java.io.InputStreamReader(httpsConnection.getInputStream()));
			String input;
			while ((input=br.readLine()) != null) { rawJson+=input; }
			br.close();
			return rawJson;
		} catch (Exception e) { e.printStackTrace(); }
		return null;
	}
	
	private static javax.net.ssl.HttpsURLConnection openHttpConnection(String location, java.util.Map<String,String> connectionProperties)
	{
		try
		{
			java.net.URL url=new java.net.URL(location);
			javax.net.ssl.HttpsURLConnection httpsConnection = (javax.net.ssl.HttpsURLConnection)url.openConnection();
			for ( java.util.Map.Entry<String, String> entry : connectionProperties.entrySet()) { httpsConnection.setRequestProperty(entry.getKey(),entry.getValue()); }
			return httpsConnection;
		} catch (Exception e) { e.printStackTrace(); }
		return null;
	}
}
