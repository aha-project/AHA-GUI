package esic;

public class JSONSteamRoller
{
	public static java.util.TreeMap<String, String> pave(String jsonToPave)
	{
		java.util.TreeMap<String, String> results=new java.util.TreeMap<>();
		int leftCBrace=0, rightCBrace=0, leftSqBrack=0, rightSqBrack=0;
		for ( int i=0; i<jsonToPave.length(); i++)
		{
			char c=jsonToPave.charAt(i);
			if (c=='{') { leftCBrace++; }
			if (c=='}') { rightCBrace++; }
			if (c=='[') { leftSqBrack++; }
			if (c==']') { rightSqBrack++; }
		}
		if (leftCBrace!=rightCBrace && leftSqBrack!=rightSqBrack) { System.err.printf("Left/Right brackets don't match. Malformed file. {=%d }=%d [=%d ]=%d\n", leftCBrace, rightCBrace, leftSqBrack, rightSqBrack); return results; }
		recursivelyPaveJson(0, "", jsonToPave.substring(1, jsonToPave.length()-1), results, false);
		
		return results;
	}
	
	private static void recursivelyPaveJson(int depth, String key, String input, java.util.TreeMap<String, String> results, boolean isArray) //internal function used to recurse into the JSON while paving
	{
		if (depth > 64)  { System.err.println("pave JSON: depth > 64, bailing, something is probably wrong"); return; }
		java.util.Vector<String> ret=scopedJsonSplitter(input);
		
		int arrayCt=0;
		String subCallKey=key;
		
		for (String s : ret) 
		{ 
			if (isArray) { subCallKey=key+"["+(arrayCt++)+"]"; }
			s=s.trim();
			if ( s==null || s.equals("") )  {  results.put(key,""); } //this should mean that we got an empty array, blank value, or something like that
			else if ( s.startsWith("{") )
			{
				s=s.substring(1, s.length()-1);
				recursivelyPaveJson(depth++, subCallKey, s, results, false);
			}
			else
			{
				int idx=s.indexOf(":"); // look for a key : value relationship. If there is none we will get -1             //TODO: presumably js does not allow the ':' character in keynames, but if this line contained only a value with a ':' in it, we will probably do sad things.
				String subKey=""; //start with an empty one in the case that we do not find a useful key (i.e. if this string is only a value)
				if (idx>=0) { subKey=s.substring(0,idx).replaceAll("^\"|\"$", ""); } //replace first and last quotes with nothingness and existential dread
				String value=s.substring(idx+1,s.length()).trim(); //if idx is not found this will result in (returned -1 plus our 1) 0 to s.length being used as value
				if (!subCallKey.equals("") && subCallKey.charAt(subCallKey.length()-1)!='.' && !subKey.equals("")) { subCallKey+="."; } //only add a dot if we have both a valid parent key and a valid child key half
				
				if ( value.startsWith("{") || value.startsWith("[") )
				{
					boolean arrayFlagToUse=false;
					if (value.startsWith("[")) { arrayFlagToUse=true;}
					value=value.substring(1, value.length()-1);
					if ( (value.startsWith("{") && !value.endsWith("}")) || (value.startsWith("[") && !value.endsWith("]")) ) { System.out.printf("This may not go well. Start and end chars don't match: '%s'\n", value);  }
					recursivelyPaveJson(depth++, subCallKey+subKey, value, results, arrayFlagToUse);
				}
				else
				{
					value=value.replaceAll("^\"|\"$", "").trim(); //replace the quotes with nothingness and trim any remaining whitespace
					results.put(subCallKey+subKey, value);
				}
			}
		}
	}
	
	private static java.util.Vector<String> scopedJsonSplitter(String input) //chunk out JSON by scope (i.e. if we're called against an array, return all the chunks of the array separate strings)
	{  //possible perf improvement: if we're only going to output a single chunk, try one or two times to remove symbols at begin/end and see if we get more chunks (some array/bracket cases right now will recurse multiple times)
		java.util.Vector<String> ret=new java.util.Vector<>();
		boolean inQuote=false;
		int previousSplit=0, numOpenBracket=0, numOpenBrace=0;
		for (int i=0;i<input.length();i++)
		{
			char c=input.charAt(i);
			if (c==',' && numOpenBrace==0 && numOpenBracket==0 && !inQuote) 
			{  
				ret.add(input.substring(previousSplit, i));
				previousSplit=i+1;
			}
			if (c=='[') { numOpenBracket++; }
			if (c==']') { numOpenBracket--; }
			if (c=='{') { numOpenBrace++; }
			if (c=='}') { numOpenBrace--; }
			if (c=='"' && !(i>=1 && input.charAt(i-1)=='\\')) { inQuote=!inQuote; } //prevent us from chunking if there's a comma within a value
		}
		ret.add(input.substring(previousSplit, input.length())); //add the last chunk
		return ret;
	}

}
