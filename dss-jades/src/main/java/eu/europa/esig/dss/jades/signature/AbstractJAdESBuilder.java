package eu.europa.esig.dss.jades.signature;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.europa.esig.dss.jades.JAdESSignatureParameters;
import eu.europa.esig.dss.jades.JAdESUtils;
import eu.europa.esig.dss.jades.validation.JWS;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.utils.Utils;
import eu.europa.esig.dss.validation.CertificateVerifier;

public abstract class AbstractJAdESBuilder implements JAdESBuilder {

	private static final Logger LOG = LoggerFactory.getLogger(JAdESCompactBuilder.class);
	
	protected final JAdESSignatureParameters parameters;
	protected final JAdESLevelBaselineB jadesLevelBaselineB;
	
	protected AbstractJAdESBuilder(final CertificateVerifier certificateVerifier, final JAdESSignatureParameters parameters, 
			final List<DSSDocument> documentsToSign) {
		Objects.requireNonNull(certificateVerifier, "CertificateVerifier must be defined!");
		Objects.requireNonNull(parameters, "SignatureParameters must be defined!");
		if (Utils.isCollectionEmpty(documentsToSign)) {
			throw new DSSException("Documents to sign must be provided!");
		}
		this.parameters = parameters;
		this.jadesLevelBaselineB = new JAdESLevelBaselineB(certificateVerifier, parameters, documentsToSign);
	}
	
	@Override
	public ToBeSigned buildDataToBeSigned() {
		assertConfigurationValidity(parameters);
		
		JWS jws = new JWS();
		incorporateHeader(jws);
		incorporatePayload(jws);
		String dataToBeSignedString = JAdESUtils.concatenate(jws.getEncodedHeader(), jws.getEncodedPayload());
		
		// The data to sign by RFC 7515 shall be ASCII-encoded
		byte[] dataToSign = JAdESUtils.getAsciiBytes(dataToBeSignedString);
		if (LOG.isTraceEnabled()) {
			LOG.trace("Data to sign: ");
			LOG.trace(new String(dataToSign));
		}
		
		return new ToBeSigned(dataToSign);
	}
	
	/**
	 * Incorporates Signed Header
	 * 
	 * @param jws {@link JWS} to populate
	 */
	protected void incorporateHeader(final JWS jws) {
		Map<String, Object> signedProperties = jadesLevelBaselineB.getSignedProperties();
		for (Map.Entry<String, Object> signedHeader : signedProperties.entrySet()) {
			jws.setHeader(signedHeader.getKey(), signedHeader.getValue());
		}
	}

	/**
	 * Incorporates Payload
	 * 
	 * @param jws {@link JWS} to populate
	 */
	protected void incorporatePayload(final JWS jws) {
		byte[] payloadBytes = jadesLevelBaselineB.getPayloadBytes();
		if (payloadBytes != null) {
			if (LOG.isTraceEnabled()) {
				LOG.trace("The payload of created signature -> {}", new String(payloadBytes));
				LOG.trace("The base64 payload of created signature -> {}", Utils.toBase64(payloadBytes));
			}
			jws.setPayloadBytes(payloadBytes);
		}
	}

	/**
	 * Verifies if the given signaturePackaging type is supported
	 * Throws an Exception if the configuration is not valid
	 */
	protected abstract void assertConfigurationValidity(final JAdESSignatureParameters signatureParameters);

}
