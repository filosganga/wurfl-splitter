package it.filosganga.wurfl.splitter;

import static ch.lambdaj.Lambda.*;
import static org.hamcrest.Matchers.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import nu.xom.*;

import org.apache.commons.cli.*;
import org.apache.commons.lang.SystemUtils;
import org.hamcrest.*;

public class Splitter {
	
	final int files;
	final File root;
	final File outputDirectory;
	final boolean gzip;

	public Splitter(int files, File root, File outputDirectory, boolean gzip) {

		this.files = files;
		this.root = root;
		this.outputDirectory = outputDirectory;
		this.gzip = gzip;
		
		outputDirectory.mkdirs();
	}
	
	public void split() {
		
		
		InputStream input = createInputStream(root);
		
		Document document = parseDocument(input);
		
		List<Element> deviceElementsList = extractDeviceElementsList(document);
		
		Element genericElement = selectFirst(deviceElementsList, new HasAttributeWithValue("id", is("generic")));
		List<Element> otherDeviceElements = select(deviceElementsList, not(genericElement));
		
		
		int size = otherDeviceElements.size() / files + (otherDeviceElements.size()%files==0?0:1);

		
		for(int findex=0; findex<files; findex++) {
			int start = findex * size;
			int end = Math.min(start + size, otherDeviceElements.size());
			
			Element rootElement = null;
			OutputStream output = null;
			
			Element devicesElement = createDevicesElement(otherDeviceElements.subList(start, end));
			if(start==0) {
				devicesElement.insertChild(genericElement.copy(), 0);
				rootElement = createRootElement(devicesElement);
				output = createRootOutput(outputDirectory, gzip);
			}
			else {
				rootElement = createPatchElement(devicesElement);
				output = createPatchOutput(outputDirectory, findex, gzip);
			}
			
			writeFile(rootElement, output);
			
			try {
				output.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		
	}

	private List<Element> extractDeviceElementsList(Document document) {
		Element wurfl = document.getRootElement();
		Element devices = wurfl.getFirstChildElement("devices");
	
		
		Elements deviceElements = devices.getChildElements("device");
		List<Element> deviceElementsList = new ArrayList<Element>(deviceElements.size());
		for(int index=0; index<deviceElements.size();index++) {
			deviceElementsList.add(deviceElements.get(index));
		}
		return deviceElementsList;
	}

	private Document parseDocument(InputStream input) {
		Document document = null;
		try {
			Builder xmlParser = new Builder();
			document = xmlParser.build(input);
		} catch (Exception e) {
			new RuntimeException(e);
		}
		return document;
	}

	private InputStream createInputStream(File root)  {
		InputStream input = null;
		
		try {
			input = new FileInputStream(root);
			if(root.getName().endsWith(",gz")) {
				input = new GZIPInputStream(input);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		} 
		
		return input;
	}
	
	private Element createDevicesElement(List<Element> deviceElementList) {

		Element devices = new Element("devices");

//		Closure addToElement = closure();
//		{
//			Element element = var(Element.class);
//			Node copy = element.copy();
//			of(devices).appendChild(copy);
//		}
//		
//		addToElement.each(deviceElementList);
		
		for(Element deviceElement : deviceElementList) {
			Node copy = deviceElement.copy();
			devices.appendChild(copy);
		}

		return devices;

	}
	
	private Element createRootElement(Element devicesElement) {
		
		Element wurflElement = new Element("wurfl");
		wurflElement.appendChild(devicesElement);
		
		return wurflElement;
	}
	
	private Element createPatchElement(Element devicesElement) {
		
		Element wurlPatchElement = new Element("wurfl_patch");
		wurlPatchElement.appendChild(devicesElement);

		return wurlPatchElement;
	}
	
	private OutputStream createRootOutput(File outputDirectory, boolean gzip) {
		
		String name = "wurfl.xml";
		if(gzip) {
			name = name + ".gz";
		}
		
		return createOutputStream(outputDirectory, name);
	}
	
	private OutputStream createPatchOutput(File outputDirectory, int index, boolean gzip) {
		
		String name = "wurfl_patch_" + index + ".xml";
		if(gzip) {
			name = name + ".gz";
		}
		
		return createOutputStream(outputDirectory, name);
	}
	
	private OutputStream createOutputStream(File outputDirectory, String name) {
		File file = new File(outputDirectory, name);
		
		OutputStream output = null;
		
		try {
			output = new FileOutputStream(file);
			if(name.endsWith(".gz")) {
				output = new GZIPOutputStream(output);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}		
		
		return output;
	}
	
	private void writeFile(Element rootElement, OutputStream output) {
		
		Document document = new Document(rootElement);
		
		Serializer serializer = null;
		
		try {
			serializer = new Serializer(output, "UTF-8");
			serializer.write(document);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} 
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		int size = 24;
		File root = null;
		File outputDirectory = SystemUtils.getJavaIoTmpDir();

		CommandLine commandLine = createCommandLine(args);
		
		if(commandLine.hasOption("n")) {
			String sizeString = commandLine.getOptionValue("n");
			size = Integer.parseInt(sizeString);
		}
		
		if(commandLine.hasOption("o")) {
			outputDirectory = new File(commandLine.getOptionValue("o"));
		}
		
		boolean gzip = commandLine.hasOption("z");
		
		List<String> otherArguments = (List<String>)commandLine.getArgList();
		if(otherArguments.size()<1) {
			System.err.println("Missed wurfl.xml file");
		}
		
		String rootPath = otherArguments.get(0);
		root = new File(rootPath);
		
		System.out.println("Initialized with size: " + size + ", and outputDirectory: " + outputDirectory + ", and wurfl: " + rootPath);
	
		Splitter splitter = new Splitter(size, root, outputDirectory, gzip);
		splitter.split();
		
	}

	private static CommandLine createCommandLine(String[] args) {
		Options options = new Options();
		options.addOption("n", "nfiles", true, "Number of files to split");
		options.addOption("o", "output-dir", true, "Output directory to store files");
		options.addOption("z", "gzip", false, "Output must be gzipped");
		
		CommandLineParser parser = new GnuParser();
		CommandLine commandLine = null;
		
		try {
			commandLine = parser.parse(options, args);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
		return commandLine;
	}


	static class HasAttributeWithValue extends TypeSafeMatcher<Element> {
		
	    private final String attributeName;
	    private final Matcher<?> value;

	    public HasAttributeWithValue(String attributeName, Matcher<?> value) {
	        this.attributeName = attributeName;
	        this.value = value;
	    }

		
		@Override
		public boolean matchesSafely(Element element) {
			
			return value.matches(element.getAttributeValue(attributeName));
		}
		
		@Override
	    public void describeTo(Description description) {
	        description.appendText("hasAttribute(");
	        description.appendValue(attributeName);
	        description.appendText(", ");
	        description.appendDescriptionOf(value);
	        description.appendText(")");
	    }

		
	    @Factory
	    public static Matcher<Element> hasAttribute(String propertyName, Matcher<?> value) {
	        return new HasAttributeWithValue(propertyName, value);
	    }
	}
	
	
}
