package eu.europa.esig.dss.jades.signature;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import eu.europa.esig.dss.enumerations.JWSSerializationType;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.jades.JAdESSignatureParameters;
import eu.europa.esig.dss.jades.JAdESUtils;
import eu.europa.esig.dss.jades.JWSJsonSerializationGenerator;
import eu.europa.esig.dss.jades.JWSJsonSerializationObject;
import eu.europa.esig.dss.jades.validation.JWS;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.MimeType;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.spi.DSSASN1Utils;
import eu.europa.esig.dss.utils.Utils;
import eu.europa.esig.dss.validation.CertificateVerifier;

/**
 * Builds a JWS JSON Serialization signature
 *
 */
public class JAdESSerializationBuilder extends AbstractJAdESBuilder {

	private JWSJsonSerializationObject jwsJsonSerializationObject;

	public JAdESSerializationBuilder(final CertificateVerifier certificateVerifier,
			final JAdESSignatureParameters parameters,
			final List<DSSDocument> documentsToSign) {
		super(certificateVerifier, parameters, documentsToSign);
	}

	public JAdESSerializationBuilder(final CertificateVerifier certificateVerifier, final JAdESSignatureParameters parameters,
			final JWSJsonSerializationObject jwsJsonSerializationObject) {
		super(certificateVerifier, parameters, extractDocumentToBeSigned(parameters, jwsJsonSerializationObject));
		this.jwsJsonSerializationObject = jwsJsonSerializationObject;
	}

	private static List<DSSDocument> extractDocumentToBeSigned(JAdESSignatureParameters parameters, JWSJsonSerializationObject jwsJsonSerializationObject) {
		if (Utils.isStringNotBlank(jwsJsonSerializationObject.getPayload())) {
			// enveloping signature
			JWS jws = jwsJsonSerializationObject.getSignatures().get(0);

			byte[] payloadBytes;
			if (jws.isRfc7797UnencodedPayload()) {
				payloadBytes = jwsJsonSerializationObject.getPayload().getBytes(StandardCharsets.UTF_8);
			} else {
				payloadBytes = JAdESUtils.fromBase64Url(jwsJsonSerializationObject.getPayload());
			}
			return Collections.singletonList(new InMemoryDocument(payloadBytes));

		} else if (Utils.isCollectionNotEmpty(parameters.getDetachedContents())) {
			// detached signature
			return parameters.getDetachedContents();

		} else {
			throw new DSSException("The payload or detached content must be provided!");
		}
	}

	@Override
	public byte[] build(SignatureValue signatureValue) {
		assertConfigurationValidity(parameters);

		JWS jws = getJWS();

		if (jwsJsonSerializationObject == null) {
			jwsJsonSerializationObject = new JWSJsonSerializationObject();
			if (!SignaturePackaging.DETACHED.equals(parameters.getSignaturePackaging())) {
				// do not include payload for detached case
				jwsJsonSerializationObject.setPayload(jws.getSignedPayload());
			}
		} else {
			assertB64ConfigurationConsistent();
		}

		byte[] signatureValueBytes = DSSASN1Utils.fromAsn1toSignatureValue(parameters.getEncryptionAlgorithm(), signatureValue.getValue());
		jws.setSignature(signatureValueBytes);

		jwsJsonSerializationObject.getSignatures().add(jws);

		JWSJsonSerializationGenerator generator = new JWSJsonSerializationGenerator(jwsJsonSerializationObject, parameters.getJwsSerializationType());
		return generator.generate();
	}

	/**
	 * All not detached signatures must have the same 'b64' value
	 */
	private void assertB64ConfigurationConsistent() {
		// verify only for non-detached cases
		if (!SignaturePackaging.DETACHED.equals(parameters.getSignaturePackaging())) {
			boolean base64UrlEncodedPayload = parameters.isBase64UrlEncodedPayload();
			for (JWS jws : jwsJsonSerializationObject.getSignatures()) {
				if (base64UrlEncodedPayload != !jws.isRfc7797UnencodedPayload()) {
					throw new DSSException("'b64' value shall be the same for all signatures! "
							+ "Change 'Base64UrlEncodedPayload' signature parameter or sign another file!");
				}
			}
		}
	}

	private JWS getJWS() {
		JWS jws = new JWS();
		incorporateHeader(jws);
		incorporatePayload(jws);
		return jws;
	}

	@Override
	public MimeType getMimeType() {
		return MimeType.JOSE_JSON;
	}

	@Override
	protected void assertConfigurationValidity(JAdESSignatureParameters signatureParameters) {
		SignaturePackaging packaging = signatureParameters.getSignaturePackaging();
		if ((packaging != SignaturePackaging.ENVELOPING) && (packaging != SignaturePackaging.DETACHED)) {
			throw new DSSException("Unsupported signature packaging for JSON Serialization Signature: " + packaging);
		}
		if (JWSSerializationType.FLATTENED_JSON_SERIALIZATION.equals(signatureParameters.getJwsSerializationType()) && jwsJsonSerializationObject != null) {
			throw new DSSException("The FLATTENED Serialization type is not supported for a document with existing signatures!");
		}
	}

}
