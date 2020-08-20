package eu.europa.esig.dss.xades.validation;

import org.apache.xml.security.signature.XMLSignatureInput;
import org.apache.xml.security.utils.resolver.ResourceResolverContext;
import org.apache.xml.security.utils.resolver.ResourceResolverException;
import org.apache.xml.security.utils.resolver.ResourceResolverSpi;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import eu.europa.esig.dss.DomUtils;
import eu.europa.esig.dss.definition.xmldsig.XMLDSigElement;
import eu.europa.esig.dss.definition.xmldsig.XMLDSigPaths;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.MimeType;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.xades.DSSXMLUtils;

/**
 * Resolver for a counter signature only.
 * 
 * Used for a counter signature extension.
 */
public class CounterSignatureResolver extends ResourceResolverSpi {
	
	private final DSSDocument document;
	
	public CounterSignatureResolver(DSSDocument document) {
		this.document = document;
	}

	@Override
	public XMLSignatureInput engineResolveURI(ResourceResolverContext context) throws ResourceResolverException {
		Node node = resolveNode(context.attr);
		
		if (node != null) {
			return createFromNode(node);
		}

		Object[] exArgs = { String.format("Unable to find a signed content by URI : '%s'", context.attr.getNodeValue()) };
		throw new ResourceResolverException("generic.EmptyMessage", exArgs, null, context.baseUri);
	}

	private XMLSignatureInput createFromNode(Node node) {
		final XMLSignatureInput result = new XMLSignatureInput(DSSXMLUtils.serializeNode(node));
		result.setMIMEType(MimeType.XML.getMimeTypeString());
		return result;
	}
	
	private boolean isXPointerSlash(String uri) {
		return uri.equals("#xpointer(/)");
	}

	@Override
	public boolean engineCanResolveURI(ResourceResolverContext context) {
		Attr uriAttr = context.attr;
		return uriAttr != null && ( DomUtils.isXPointerQuery(uriAttr.getNodeValue()) || DomUtils.isElementReference(uriAttr.getNodeValue()) ) 
				&& resolveNode(uriAttr) != null;
	}
	
	private Node resolveNode(Attr uriAttr) {
		if (uriAttr == null) {
			return null;
		}
		
		String uriValue = DSSUtils.decodeUrl(uriAttr.getNodeValue());
			
		Document documentDom = DomUtils.buildDOM(document);
		Node node = DomUtils.getNode(documentDom, XMLDSigPaths.ALL_SIGNATURE_VALUES_PATH + DomUtils.getXPathByIdAttribute(uriValue));
		
		if (node == null && isXPointerSlash(uriValue) && XMLDSigElement.SIGNATURE_VALUE.getTagName().equals(documentDom.getLocalName())) {
			node = documentDom;
		} else if (node == null && DomUtils.isXPointerQuery(uriValue)) {
			String xPointerId = DomUtils.getXPointerId(uriValue);
			node = DomUtils.getNode(documentDom, XMLDSigPaths.ALL_SIGNATURE_VALUES_PATH + DomUtils.getXPathByIdAttribute(xPointerId));
		}
		
		if (node != null) {
			return node;
		}
		
		return null;
	}

}
