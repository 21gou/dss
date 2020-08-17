package eu.europa.esig.dss.jades.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.JWSSerializationType;
import eu.europa.esig.dss.enumerations.SigDMechanism;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.jades.JAdESSignatureParameters;
import eu.europa.esig.dss.jades.signature.JAdESService;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.model.DigestDocument;
import eu.europa.esig.dss.model.FileDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.test.PKIFactoryAccess;
import eu.europa.esig.dss.utils.Utils;

public class JAdESImpossibleExtensionTest extends PKIFactoryAccess {

	@Test
	public void notSigned() {
		DSSDocument doc = new FileDocument("src/test/resources/sample.json");

		JAdESService service = new JAdESService(getOfflineCertificateVerifier());
		service.setTspSource(getGoodTsa());

		JAdESSignatureParameters parameters = new JAdESSignatureParameters();
		parameters.setSignatureLevel(SignatureLevel.JAdES_BASELINE_T);

		DSSException exception = assertThrows(DSSException.class, () -> service.extendDocument(doc, parameters));
		assertEquals("There is no signature to extend!", exception.getMessage());
	}
	
	@Test
	public void digestDocumentWithLTALevelTest() {
		DSSDocument doc = new FileDocument("src/test/resources/sample.json");
		DigestDocument digestDocument = new DigestDocument(DigestAlgorithm.SHA256, 
				Utils.toBase64(DSSUtils.digest(DigestAlgorithm.SHA256, doc)), "sample");

		JAdESService service = new JAdESService(getCompleteCertificateVerifier());
		service.setTspSource(getGoodTsa());

		JAdESSignatureParameters parameters = new JAdESSignatureParameters();
		parameters.setSigningCertificate(getSigningCert());
		parameters.setCertificateChain(getCertificateChain());
		parameters.setJwsSerializationType(JWSSerializationType.FLATTENED_JSON_SERIALIZATION);
		parameters.setSignaturePackaging(SignaturePackaging.DETACHED);
		parameters.setSigDMechanism(SigDMechanism.OBJECT_ID_BY_URI_HASH);
		parameters.setSignatureLevel(SignatureLevel.JAdES_BASELINE_LT);
		
		ToBeSigned dataToSign = service.getDataToSign(digestDocument, parameters);
		SignatureValue signatureValue = getToken().sign(dataToSign, parameters.getDigestAlgorithm(), getPrivateKeyEntry());
		DSSDocument signedDocument = service.signDocument(digestDocument, parameters, signatureValue);

		JAdESSignatureParameters extensionParameters = new JAdESSignatureParameters();
		extensionParameters.setSignatureLevel(SignatureLevel.JAdES_BASELINE_LTA);
		extensionParameters.setDetachedContents(Collections.singletonList(digestDocument));

		DSSException exception = assertThrows(DSSException.class, () -> service.extendDocument(signedDocument, extensionParameters));
		assertEquals("JAdES-LTA with All data Timestamp requires complete binaries of signed documents! "
				+ "Extension with a DigestDocument is not possible.", exception.getMessage());
	}

	@Override
	protected String getSigningAlias() {
		return GOOD_USER;
	}

}
