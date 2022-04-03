package de.sk9.commons.imaging;

import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPMeta;

@SuppressWarnings("serial")
public class JpegXmpDataException extends Exception {

	public JpegXmpDataException(XMPMeta xmpMeta, XMPException e) {
		super(xmpMeta.toString(), e);
	}

	public JpegXmpDataException(Exception e) {
		super(e);
	}
}
