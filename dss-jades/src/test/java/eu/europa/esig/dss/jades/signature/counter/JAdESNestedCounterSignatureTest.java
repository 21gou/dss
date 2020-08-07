package eu.europa.esig.dss.jades.signature.counter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.europa.esig.dss.diagnostic.DiagnosticData;
import eu.europa.esig.dss.diagnostic.SignatureWrapper;
import eu.europa.esig.dss.enumerations.JWSSerializationType;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.jades.JAdESSignatureParameters;
import eu.europa.esig.dss.jades.signature.JAdESService;
import eu.europa.esig.dss.jades.validation.AbstractJAdESTestValidation;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.FileDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.validation.AdvancedSignature;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;

public class JAdESNestedCounterSignatureTest extends AbstractJAdESTestValidation {
	
	private JAdESService service;
	private DSSDocument documentToSign;
	private JAdESSignatureParameters signatureParameters;
	private JAdESCounterSignatureParameters counterSignatureParameters;
	
	@BeforeEach
	public void init() {
		documentToSign = new FileDocument(new File("src/test/resources/sample.json"));
		
		service = new JAdESService(getCompleteCertificateVerifier());
		
		signatureParameters = new JAdESSignatureParameters();
		signatureParameters.bLevel().setSigningDate(new Date());
		signatureParameters.setSigningCertificate(getSigningCert());
		signatureParameters.setCertificateChain(getCertificateChain());
		signatureParameters.setSignaturePackaging(SignaturePackaging.ENVELOPING);
		signatureParameters.setSignatureLevel(SignatureLevel.JAdES_BASELINE_B);
		signatureParameters.setJwsSerializationType(JWSSerializationType.JSON_SERIALIZATION);
		
		counterSignatureParameters = new JAdESCounterSignatureParameters();
		counterSignatureParameters.bLevel().setSigningDate(new Date());
		counterSignatureParameters.setSigningCertificate(getSigningCert());
		counterSignatureParameters.setCertificateChain(getCertificateChain());
		counterSignatureParameters.setSignatureLevel(SignatureLevel.JAdES_BASELINE_B);
		counterSignatureParameters.setJwsSerializationType(JWSSerializationType.FLATTENED_JSON_SERIALIZATION);
	}
	
	@Test
	public void test() throws Exception {
		ToBeSigned dataToSign = service.getDataToSign(documentToSign, signatureParameters);
		SignatureValue signatureValue = getToken().sign(dataToSign, signatureParameters.getDigestAlgorithm(),
				signatureParameters.getMaskGenerationFunction(), getPrivateKeyEntry());
		DSSDocument signedDocument = service.signDocument(documentToSign, signatureParameters, signatureValue);
		
		ToBeSigned dataToBeCounterSigned = service.getDataToBeCounterSigned(signedDocument, counterSignatureParameters);
		signatureValue = getToken().sign(dataToBeCounterSigned, counterSignatureParameters.getDigestAlgorithm(),
				counterSignatureParameters.getMaskGenerationFunction(), getPrivateKeyEntry());
		DSSDocument counterSignedSignature = service.counterSignSignature(signedDocument, counterSignatureParameters, signatureValue);
		
		counterSignedSignature.save("target/counterSignedSignature.json");
		
		SignedDocumentValidator validator = getValidator(counterSignedSignature);
		
		List<AdvancedSignature> signatures = validator.getSignatures();
		assertEquals(1, signatures.size());
		
		AdvancedSignature advancedSignature = signatures.get(0);
		List<AdvancedSignature> counterSignatures = advancedSignature.getCounterSignatures();
		assertEquals(1, counterSignatures.size());
		
		AdvancedSignature counterSignature = counterSignatures.get(0);
		assertNotNull(counterSignature.getMasterSignature());
		assertEquals(0, counterSignature.getCounterSignatures().size());
		
		counterSignatureParameters.bLevel().setSigningDate(new Date());
		counterSignatureParameters.setSigningSignatureId(counterSignature.getId());
		
		dataToBeCounterSigned = service.getDataToBeCounterSigned(counterSignedSignature, counterSignatureParameters);
		signatureValue = getToken().sign(dataToBeCounterSigned, counterSignatureParameters.getDigestAlgorithm(),
				counterSignatureParameters.getMaskGenerationFunction(), getPrivateKeyEntry());
		DSSDocument nestedCounterSignedSignature = service.counterSignSignature(counterSignedSignature, counterSignatureParameters, signatureValue);
		
		nestedCounterSignedSignature.save("target/nestedCounterSignature.json");
		
		validator = getValidator(nestedCounterSignedSignature);
		
		signatures = validator.getSignatures();
		assertEquals(1, signatures.size());
		
		advancedSignature = signatures.get(0);
		counterSignatures = advancedSignature.getCounterSignatures();
		assertEquals(1, counterSignatures.size());
		
		counterSignature = counterSignatures.get(0);
		assertNotNull(counterSignature.getMasterSignature());
		assertEquals(1, counterSignature.getCounterSignatures().size());
		
		Reports reports = verify(nestedCounterSignedSignature);
		DiagnosticData diagnosticData = reports.getDiagnosticData();
		
		List<SignatureWrapper> signatureWrappers = diagnosticData.getSignatures();
		assertEquals(3, signatureWrappers.size());
		
		boolean rootSignatureFound = false;
		boolean counterSignatureFound = false;
		boolean nestedCounterSignatureFound = false;
		for (SignatureWrapper signatureWrapper : signatureWrappers) {
			if (!signatureWrapper.isCounterSignature()) {
				rootSignatureFound = true;
			} else if (signatureWrapper.getParent() != null && signatureWrapper.getParent().getParent() == null) {
				counterSignatureFound = true;
			} else if (signatureWrapper.getParent() != null && signatureWrapper.getParent().getParent() != null) {
				nestedCounterSignatureFound = true;
			}
		}
		assertTrue(rootSignatureFound);
		assertTrue(counterSignatureFound);
		assertTrue(nestedCounterSignatureFound);
	}
	
	@Override
	public void validate() {
		// do nothing
	}

	@Override
	protected DSSDocument getSignedDocument() {
		return null;
	}
	
	@Override
	protected String getSigningAlias() {
		return GOOD_USER;
	}

}
