package eu.europa.esig.dss.xades.signature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;

import eu.europa.esig.dss.diagnostic.DiagnosticData;
import eu.europa.esig.dss.diagnostic.SignatureWrapper;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.FileDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.signature.MultipleDocumentsSignatureService;
import eu.europa.esig.dss.utils.Utils;
import eu.europa.esig.dss.validation.AdvancedSignature;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.xades.XAdESSignatureParameters;
import eu.europa.esig.dss.xades.XAdESTimestampParameters;

public class XAdESLevelLTAMultipleDocsDetachedCounterSignatureTest extends AbstractXAdESMultipleDocumentsSignatureService {

	private XAdESService service;
	private List<DSSDocument> documentsToSign;

	private Date signingDate;
	
	private String signatureId;

	@BeforeEach
	public void init() throws Exception {
		service = new XAdESService(getCompleteCertificateVerifier());
		service.setTspSource(getGoodTsa());
		
		FileDocument f1 = new FileDocument(new File("src/test/resources/sample-with-id.xml"));
		FileDocument f2 = new FileDocument(new File("src/test/resources/sample-with-different-id.xml"));
		documentsToSign = Arrays.<DSSDocument>asList(f1, f2);
		
		signingDate = new Date();
	}

	@Override
	protected XAdESSignatureParameters getSignatureParameters() {
		XAdESSignatureParameters signatureParameters = new XAdESSignatureParameters();
		signatureParameters.bLevel().setSigningDate(signingDate);
		signatureParameters.setSigningCertificate(getSigningCert());
		signatureParameters.setCertificateChain(getCertificateChain());
		signatureParameters.setSignaturePackaging(SignaturePackaging.DETACHED);
		signatureParameters.setSignatureLevel(SignatureLevel.XAdES_BASELINE_LTA);
		return signatureParameters;
	}

	protected XAdESCounterSignatureParameters getCounterSignatureParameters() {
		XAdESCounterSignatureParameters signatureParameters = new XAdESCounterSignatureParameters();
		signatureParameters.bLevel().setSigningDate(signingDate);
		signatureParameters.setSigningCertificate(getSigningCert());
		signatureParameters.setCertificateChain(getCertificateChain());
		signatureParameters.setSignatureLevel(SignatureLevel.XAdES_BASELINE_LTA);
		return signatureParameters;
	}

	@Override
	protected DSSDocument sign() {
		ToBeSigned dataToSign = service.getDataToSign(documentsToSign, getSignatureParameters());
		SignatureValue signatureValue = getToken().sign(dataToSign, getSignatureParameters().getDigestAlgorithm(), getPrivateKeyEntry());
		DSSDocument signedDocument = service.signDocument(documentsToSign, getSignatureParameters(), signatureValue);

		SignedDocumentValidator validator = getValidator(signedDocument);

		List<AdvancedSignature> signatures = validator.getSignatures();
		assertTrue(Utils.isCollectionNotEmpty(signatures));
		
		AdvancedSignature signature = signatures.get(signatures.size() - 1);
		signatureId = signature.getId();
		
		XAdESCounterSignatureParameters counterSignatureParameters = getCounterSignatureParameters();
		counterSignatureParameters.setSignatureIdToCounterSign(signatureId);
		
		dataToSign = service.getDataToBeCounterSigned(signedDocument, counterSignatureParameters);
		signatureValue = getToken().sign(dataToSign, counterSignatureParameters.getDigestAlgorithm(),
				counterSignatureParameters.getMaskGenerationFunction(), getPrivateKeyEntry());
		return service.counterSignSignature(signedDocument, counterSignatureParameters, signatureValue);
	}
	
	@Override
	protected void checkNumberOfSignatures(DiagnosticData diagnosticData) {
		assertEquals(2, diagnosticData.getSignatureIdList().size());
	}
	
	@Override
	protected void verifyOriginalDocuments(SignedDocumentValidator validator, DiagnosticData diagnosticData) {
		SignatureWrapper signatureWrapper = diagnosticData.getSignatureById(signatureId);
		assertNotNull(signatureWrapper);
		
		List<DSSDocument> originalDocuments = validator.getOriginalDocuments(signatureId);
		assertEquals(2, originalDocuments.size());
		
		Set<SignatureWrapper> counterSignatures = diagnosticData.getAllCounterSignaturesForMasterSignature(signatureWrapper);
		assertEquals(1, counterSignatures.size());
		
		SignatureWrapper counterSignature = counterSignatures.iterator().next();
		
		originalDocuments = validator.getOriginalDocuments(counterSignature.getId());
		assertEquals(0, originalDocuments.size());
	}
	
	@Override
	protected List<DSSDocument> getDetachedContents() {
		return documentsToSign;
	}

	@Override
	protected List<DSSDocument> getDocumentsToSign() {
		return documentsToSign;
	}

	@Override
	protected MultipleDocumentsSignatureService<XAdESSignatureParameters, XAdESTimestampParameters> getService() {
		return service;
	}

	@Override
	protected String getSigningAlias() {
		return GOOD_USER;
	}

}
