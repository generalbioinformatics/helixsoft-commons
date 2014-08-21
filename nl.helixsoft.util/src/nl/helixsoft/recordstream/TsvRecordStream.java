package nl.helixsoft.recordstream;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;


//TODO: rename to FileRecordStream

/**
 * Turn a stream of delimited values into a record stream.
 */
public class TsvRecordStream extends AbstractRecordStream
{
	public static int FILTER_COMMENTS = 0x100;
	public static int NO_HEADER = 0x200;
	
	/** If this flag is on, check for each header and row field if it is enclosed in double quotes, and remove them */
	public static int REMOVING_OPTIONAL_QUOTES = 0x400;

	@Deprecated
	public static int TAB_DELIMITED = 0x000; // default, so zero.
	@Deprecated
	public static int COMMA_DELIMITED = 0x800;

	private int flags = 0;
	private final BufferedReader reader;
	private final RecordMetaData rmd;
	private String delimiter = "\t";
	
	public static TsvRecordStreamBuilder open (Reader _reader)
	{
		return new TsvRecordStreamBuilder(_reader);	
	}

	public static TsvRecordStreamBuilder open (InputStream _is)
	{
		return new TsvRecordStreamBuilder(_is);	
	}

	public static TsvRecordStreamBuilder open (File _file) throws FileNotFoundException
	{
		return new TsvRecordStreamBuilder(_file);	
	}
	
	public static class TsvRecordStreamBuilder
	{
		private final Reader reader;
		private String delimiter = "\t";
		private int flags;
		private String[] header = null;
		
		TsvRecordStreamBuilder(Reader _reader)
		{
			this.reader = _reader;
		}

		TsvRecordStreamBuilder(File f) throws FileNotFoundException
		{
			this.reader = new FileReader (f);
		}

		TsvRecordStreamBuilder(InputStream is)
		{
			this.reader = new InputStreamReader (is);
		}

		public TsvRecordStreamBuilder tabSeparated()
		{
			delimiter = "\t";
			return this;
		}
		
		public TsvRecordStreamBuilder commaSeparated()
		{
			delimiter = ",";
			return this;
		}
		
		public TsvRecordStreamBuilder customSeparator(String regex)
		{
			delimiter = regex;
			return this;
		}

		public TsvRecordStreamBuilder removeOptionalQuotes()
		{
			//TODO: for the combination of commaSeparated and removeOptionalQuotes, use the function StringUtils.quotedCommaSplit, to deal correctly with comma's inside quotes
			flags |= REMOVING_OPTIONAL_QUOTES;
			return this;
		}

		public TsvRecordStreamBuilder firstLineIsHeader()
		{
			if ((flags & NO_HEADER) > 0) flags -= NO_HEADER;
			return this;
		}
		
		public TsvRecordStreamBuilder setHeader(String[] header)
		{
			this.header = header;
			flags |= NO_HEADER;
			return this;
		}

		public TsvRecordStreamBuilder setHeader(List<String> header)
		{
			this.header = header.toArray(new String[header.size()]);
			flags |= NO_HEADER;
			return this;
		}

		public TsvRecordStreamBuilder filterComments()
		{
			flags |= FILTER_COMMENTS;
			return this;
		}
		
		public TsvRecordStream get() throws RecordStreamException
		{
			if (header == null)
			{
				return new TsvRecordStream (reader, delimiter, flags);
			}
			else
			{
				return new TsvRecordStream (reader, delimiter, header, flags);
			}
		}

	}
	
	public TsvRecordStream (Reader _reader, String _delimiter, String[] _header, int flags) throws RecordStreamException
	{
		this.flags = flags;
		if ((flags & COMMA_DELIMITED) > 0)
		{
			delimiter = ",";
		}
		else
		{
			delimiter = _delimiter;
		}
		
		this.reader = new BufferedReader(_reader);
		rmd = new DefaultRecordMetaData (_header);		
	}
	
	@Deprecated
	public TsvRecordStream (Reader _reader, String[] _header) throws RecordStreamException
	{
		this (_reader, "\t", _header, 0);
	}

	// TODO: this constructor has some redundancy with TsvRecordStream(Reader, String, int)   
	public TsvRecordStream (Reader _reader, String[] _header, int flags) throws RecordStreamException
	{
		this (_reader, "\t", _header, flags);
	}

	@Deprecated
	public TsvRecordStream (Reader _reader) throws RecordStreamException
	{
		this (_reader, "\t", 0);
	}

	private String removeOptionalQuotes(String in)
	{
		if (in.startsWith("\"") && in.endsWith("\""))
		{
			return in.substring (1, in.length() - 1);
		}
		else
			return in;
	}

	@Deprecated
	public TsvRecordStream (Reader _reader, int flags) throws RecordStreamException
	{
		this (_reader, "\t", flags);
	}
	
	// TODO: this constructor has some redundancy with TsvRecordStream(Reader, String, String[], int)
	public TsvRecordStream (Reader _reader, String _delimiter, int flags) throws RecordStreamException
	{
		this.flags = flags;
		if ((flags & COMMA_DELIMITED) > 0)
		{
			delimiter = ",";
		}
		else
		{
			delimiter = _delimiter;
		}
		
		try 
		{
			this.reader = new BufferedReader(_reader);
			String headerLine = getNextNonCommentLine();
			List<String> header = new ArrayList<String>();
			if (headerLine != null) // empty file has no header
			{
				for (String h : headerLine.split(delimiter))
				{
					if ((flags & REMOVING_OPTIONAL_QUOTES) > 0)
					{
						header.add (removeOptionalQuotes(h));
					}
					else
						header.add (h);
				}
			}
			
			rmd = new DefaultRecordMetaData(header);
		} 
		catch (IOException e) 
		{
			throw new RecordStreamException(e);
		}
	}

	@Override
	public Record getNext() throws StreamException 
	{
		try 
		{
			String line;
			// fetch next line that doesn't start with "#"
			line = getNextNonCommentLine();
			if (line == null) return null;
			
			String[] split = line.split(delimiter, -1);
			
			String[] fields;
			if (split.length == rmd.getNumCols())
			{
				fields = split;
				if ((flags & REMOVING_OPTIONAL_QUOTES) > 0)
				{
					for (int col = 0; col < rmd.getNumCols(); ++col)
					{
						fields[col] = removeOptionalQuotes(fields[col]);
					}
				}
			}
			else
			{
				// ensure that array of fields is the expected length
				fields = new String[rmd.getNumCols()];
				int col = 0;
				for (String field : split)
				{
					if ((flags & REMOVING_OPTIONAL_QUOTES) > 0)
						fields[col] = removeOptionalQuotes(field);
					else
						fields[col] = field;
					
					col++;
					if (col == rmd.getNumCols()) 
					{
						// there are extra columns at the end. Check if they are empty or if they contain data.
						for (int i = col; i < split.length; ++i)
						{
							if (!split[col].equals(""))
							{
								System.err.println ("Warning: found extra non-empty columns in TSV file");
								break; // ignoring extra column
							}
						}
						break;
					}
				}
			}
			return new DefaultRecord(rmd, fields);
		} 
		catch (IOException e) 
		{
			throw new StreamException(e);
		}
	}

	private String getNextNonCommentLine() throws IOException 
	{
		if  ((flags | FILTER_COMMENTS) == 0) return reader.readLine();
		
		String line;
		do {
			line = reader.readLine();
			if (line == null) return null;
		} 
		while (line.startsWith("#"));
		return line;
	}

	@Override
	public RecordMetaData getMetaData() 
	{
		return rmd;
	}
}
