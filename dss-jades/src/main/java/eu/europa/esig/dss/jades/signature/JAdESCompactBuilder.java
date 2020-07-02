package eu.europa.esig.dss.jades.signature;

import java.util.List;

import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.jades.JAdESSignatureParameters;
import eu.europa.esig.dss.jades.JAdESUtils;
import eu.europa.esig.dss.jades.validation.JWS;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.model.MimeType;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.spi.DSSASN1Utils;
import eu.europa.esig.dss.validation.CertificateVerifier;

public class JAdESCompactBuilder extends AbstractJAdESBuilder {
	
	public JAdESCompactBuilder(final CertificateVerifier certificateVerifier, final JAdESSignatureParameters parameters, 
			final List<DSSDocument> documentsToSign) {
		super(certificateVerifier, parameters, documentsToSign);
	}

	/**
	 * Builds the concatenation of signed header and payload (dataTobeSigned string) in the way :
	 * BASE64URL(UTF8(JWS Protected Header)) || '.' || BASE64URL(JWS Payload)
	 * 
	 * @return {@link String} representing the concatenation result
	 */
	@Override
	public byte[] build(SignatureValue signatureValue) {
		assertConfigurationValidity(parameters);
		
		JWS jws = new JWS();
		incorporateHeader(jws);
		if (!SignaturePackaging.DETACHED.equals(parameters.getSignaturePackaging())) {
			incorporatePayload(jws);
		}
		String payload = parameters.isBase64UrlEncodedPayload() ? jws.getEncodedPayload() : jws.getUnverifiedPayload();
		byte[] signatureValueBytes = DSSASN1Utils.fromAsn1toSignatureValue(parameters.getEncryptionAlgorithm(), signatureValue.getValue());
		
		String signatureString = JAdESUtils.concatenate(jws.getEncodedHeader(), payload, JAdESUtils.toBase64Url(signatureValueBytes));
		return signatureString.getBytes();
	}

	@Override
	public MimeType getMimeType() {
		return MimeType.JOSE;
	}

	@Override
	protected void assertConfigurationValidity(JAdESSignatureParameters signatureParameters) {
		SignaturePackaging packaging = signatureParameters.getSignaturePackaging();
		if ((packaging != SignaturePackaging.ENVELOPING) && (packaging != SignaturePackaging.DETACHED)) {
			throw new DSSException("Unsupported signature packaging for JAdES Compact Signature: " + packaging);
		}
		SignatureLevel signatureLevel = signatureParameters.getSignatureLevel();
		if (!SignatureLevel.JAdES_BASELINE_B.equals(signatureLevel)) {
			throw new DSSException("Only JAdES_BASELINE_B level is allowed for JAdES Compact Signature!");
		}
	}

}
