package eu.europa.esig.dss.jades.signature;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.europa.esig.dss.enumerations.JWSSerializationType;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.jades.JAdESSignatureParameters;
import eu.europa.esig.dss.jades.JAdESTimestampParameters;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.model.FileDocument;
import eu.europa.esig.dss.signature.DocumentSignatureService;

public class JAdESSerializationWithExtraElementSignatureTest extends AbstractJAdESTestSignature {

	private DocumentSignatureService<JAdESSignatureParameters, JAdESTimestampParameters> service;
	private DSSDocument documentToSign;
	private JAdESSignatureParameters signatureParameters;

	@BeforeEach
	public void init() throws Exception {
		documentToSign = new FileDocument(new File("src/test/resources/validation/serialization-extra-element.json"));
		
		service = new JAdESService(getCompleteCertificateVerifier());
		service.setTspSource(getGoodTsa());
		
		signatureParameters = new JAdESSignatureParameters();
		signatureParameters.bLevel().setSigningDate(new Date());
		signatureParameters.setSigningCertificate(getSigningCert());
		signatureParameters.setCertificateChain(getCertificateChain());
		signatureParameters.setSignaturePackaging(SignaturePackaging.ENVELOPING);
		signatureParameters.setSignatureLevel(SignatureLevel.JAdES_BASELINE_B);
		signatureParameters.setJwsSerializationType(JWSSerializationType.JSON_SERIALIZATION);
	}
	
	@Test
	@Override
	public void signAndVerify() {
		Exception exception = assertThrows(DSSException.class, () -> super.sign());
		assertTrue(exception.getMessage().contains("JWS Serialization is not supported for invalid RFC 7515 files."));
		assertTrue(exception.getMessage().contains("extraneous key [evil] is not permitted"));
	}

	@Override
	protected DSSDocument getDocumentToSign() {
		return documentToSign;
	}

	@Override
	protected DocumentSignatureService<JAdESSignatureParameters, JAdESTimestampParameters> getService() {
		return service;
	}

	@Override
	protected JAdESSignatureParameters getSignatureParameters() {
		return signatureParameters;
	}

	@Override
	protected String getSigningAlias() {
		return GOOD_USER;
	}

}
