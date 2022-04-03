package de.sk9.commons.imaging;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.common.bytesource.ByteSource;
import org.apache.commons.imaging.common.bytesource.ByteSourceFile;
import org.apache.commons.imaging.formats.jpeg.JpegImageParser;
import org.apache.commons.imaging.formats.jpeg.JpegImagingParameters;
import org.apache.commons.imaging.formats.jpeg.xmp.JpegXmpRewriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPIterator;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.XMPMetaFactory;
import com.adobe.internal.xmp.options.IteratorOptions;
import com.adobe.internal.xmp.options.PropertyOptions;
import com.adobe.internal.xmp.properties.XMPProperty;
import com.adobe.internal.xmp.properties.XMPPropertyInfo;

public class JpegXmpData {
	private static Logger LOG = LogManager.getLogger(JpegXmpData.class);

	private static final PropertyQualifier<String> KEYWORD_QUALIFIER = new PropertyQualifier<>(
			"http://purl.org/dc/elements/1.1/", "dc:subject",
			String.class, s -> s, s -> s);
	private static final PropertyQualifier<Integer> IMAGE_RATING_QUALIFIER = new PropertyQualifier<Integer>(
			"http://ns.adobe.com/xap/1.0/", "xmp:Rating",
			Integer.class, s -> Integer.valueOf(s), v -> v.toString());

	private XMPMeta xmpMeta;

	private JpegXmpData(XMPMeta xmpMeta) {
		this.xmpMeta = xmpMeta;
	}

	public JpegXmpData() {
	}

	public static JpegXmpData fromFile(File jpegFile) throws JpegXmpDataException {
		try {
			ByteSource byteSource = new ByteSourceFile(jpegFile);
			JpegImagingParameters params = new JpegImagingParameters();
			String xmpXml = new JpegImageParser().getXmpXml(byteSource, params);

			if (xmpXml != null) {
				XMPMeta xmpMeta = XMPMetaFactory.parseFromBuffer(xmpXml.getBytes());
				return new JpegXmpData(xmpMeta);
			} else {
				return new JpegXmpData(null);
			}
		} catch (Exception e) {
			throw new JpegXmpDataException(e);
		}
	}

	public List<String> getKeywords() throws JpegXmpDataException {
		return getXmpArrayValues(KEYWORD_QUALIFIER);
	}

	public void addKeywords(List<String> keywordList) throws JpegXmpDataException {
		addKeywords(keywordList.toArray(new String[0]));
	}

	public void addKeywords(String... keywords) throws JpegXmpDataException {
		addXmpArrayValues(KEYWORD_QUALIFIER, keywords);
	}

	public void replaceKeywords(List<String> keywordList) throws JpegXmpDataException {
		replaceKeywords(keywordList.toArray(new String[0]));
	}

	public void replaceKeywords(String... keywords) throws JpegXmpDataException {
		replaceXmpArrayValues(KEYWORD_QUALIFIER, keywords);
	}

	public void removeKeywords(List<String> keywordList) throws JpegXmpDataException {
		removeKeywords(keywordList.toArray(new String[0]));
	}

	public void removeKeywords(String... keywords) throws JpegXmpDataException {
		removeXmpArrayValue(KEYWORD_QUALIFIER, keywords);
	}

	public void removeAllKeywords() {
		removeAllXmpArrayValues(KEYWORD_QUALIFIER);
	}

	public Integer getImageRating() throws JpegXmpDataException {
		return getXmpValue(IMAGE_RATING_QUALIFIER);
	}

	public void replaceInFile(File inFile) throws JpegXmpDataException {
		try {
			JpegXmpRewriter rewriter = new JpegXmpRewriter();
			ByteSource byteSource = new ByteSourceFile(inFile);
			File outFile = File.createTempFile("JpegXmpData", ".jpg");
			FileOutputStream fos = new FileOutputStream(outFile);
			outFile.deleteOnExit();

			if (xmpMeta == null) {
				rewriter.removeXmpXml(byteSource, fos);
			} else {
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				XMPMetaFactory.serialize(xmpMeta, bos);
				rewriter.updateXmpXml(byteSource, fos, bos.toString(StandardCharsets.UTF_8));

				Files.move(outFile.toPath(), inFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				outFile.delete();
			}
		} catch (XMPException e) {
			throw new JpegXmpDataException(xmpMeta, e);
		} catch (ImageReadException | ImageWriteException | IOException e) {
			throw new JpegXmpDataException(e);
		}
	}

	@Override
	public String toString() {
		if (xmpMeta != null) {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			try {
				XMPMetaFactory.serialize(xmpMeta, bos);
			} catch (XMPException e) {
				e.printStackTrace();
			}
			return bos.toString(StandardCharsets.UTF_8);
		} else {
			return "xmpMeta not initialized";
		}
	}

	private void garanteeXmpMeta() {
		if (xmpMeta == null) {
			LOG.debug("xmpMeta not present - creating struct");
			xmpMeta = XMPMetaFactory.create();
		}
	}

	private <T> T getXmpValue(PropertyQualifier<T> qualifier) throws JpegXmpDataException {
		if (xmpMeta == null) {
			return null;
		} else {
			try {
				String stringValue = xmpMeta.getProperty(qualifier.namespace(), qualifier.propertyName()).getValue();
				return qualifier.stringToValue().apply(stringValue);
			} catch (XMPException e) {
				throw new JpegXmpDataException(xmpMeta, e);
			}
		}
	}

	private <T> List<T> getXmpArrayValues(PropertyQualifier<T> qualifier) throws JpegXmpDataException {
		try {
			List<T> result = Collections.emptyList();
			if (xmpMeta != null) {
				IteratorOptions iteratorOptions = new IteratorOptions();
				iteratorOptions.setOptions(IteratorOptions.JUST_CHILDREN);
				XMPIterator iterator = xmpMeta.iterator(qualifier.namespace(), qualifier.propertyName(),
						iteratorOptions);
				while (iterator.hasNext()) {
					XMPPropertyInfo property = (XMPPropertyInfo) iterator.next();
					if (result.isEmpty()) {
						result = new ArrayList<>();
					}
					result.add(qualifier.stringToValue().apply(property.getValue()));
				}
			}
			return result;
		} catch (XMPException e) {
			throw new JpegXmpDataException(xmpMeta, e);
		}
	}

	@SafeVarargs
	private <T> void addXmpArrayValues(PropertyQualifier<T> qualifier, T... values) throws JpegXmpDataException {
		garanteeXmpMeta();
		try {
			List<String> existingKeywords = getKeywords();
			for (T value : values) {
				if (!existingKeywords.contains(value)) {
					xmpMeta.appendArrayItem(qualifier.namespace(), qualifier.propertyName(),
							new PropertyOptions(PropertyOptions.ARRAY),
							qualifier.valueToString().apply(value),
							new PropertyOptions(PropertyOptions.NO_OPTIONS));
				}
			}
		} catch (XMPException e) {
			throw new JpegXmpDataException(xmpMeta, e);
		}
	}

	@SafeVarargs
	private <T> void replaceXmpArrayValues(PropertyQualifier<T> qualifier, T... values)
			throws JpegXmpDataException {
		garanteeXmpMeta();
		try {
			removeAllXmpArrayValues(qualifier);
			for (T value : values) {
				xmpMeta.appendArrayItem(qualifier.namespace(), qualifier.propertyName(),
						new PropertyOptions(PropertyOptions.ARRAY),
						qualifier.valueToString().apply(value),
						new PropertyOptions(PropertyOptions.NO_OPTIONS));
			}
		} catch (XMPException e) {
			throw new JpegXmpDataException(xmpMeta, e);
		}
	}

	public <T> void removeAllXmpArrayValues(PropertyQualifier<T> qualifier) {
		if (xmpMeta == null) {
			return;
		} else {
			xmpMeta.deleteProperty(qualifier.namespace(), qualifier.propertyName());
		}
	}

	@SafeVarargs
	private <T> void removeXmpArrayValue(PropertyQualifier<T> qualifier, T... values) throws JpegXmpDataException {
		if (xmpMeta == null) {
			return;
		} else {
			List<T> keywordList = Arrays.asList(values);
			try {
				int keywordCount = xmpMeta.countArrayItems(qualifier.namespace(), qualifier.propertyName());
				for (int i = keywordCount; i > 0; i--) {
					XMPProperty keyword = xmpMeta.getArrayItem(qualifier.namespace(), qualifier.propertyName(), i);
					if (keywordList.contains(keyword.getValue())) {
						xmpMeta.deleteArrayItem(qualifier.namespace(), qualifier.propertyName(), i);
					}
				}
			} catch (XMPException e) {
				throw new JpegXmpDataException(xmpMeta, e);
			}
		}
	}
}
