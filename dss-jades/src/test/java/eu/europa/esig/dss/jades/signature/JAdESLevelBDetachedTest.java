package eu.europa.esig.dss.jades.signature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jose4j.json.JsonUtil;
import org.jose4j.jwx.HeaderParameterNames;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.BeforeEach;

import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.jades.JAdESHeaderParameterNames;
import eu.europa.esig.dss.jades.JAdESSignatureParameters;
import eu.europa.esig.dss.jades.JAdESTimestampParameters;
import eu.europa.esig.dss.jades.JAdESUtils;
import eu.europa.esig.dss.jades.JWSCompactSerializationParser;
import eu.europa.esig.dss.jades.JWSConverter;
import eu.europa.esig.dss.jades.validation.JWS;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.FileDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.signature.DocumentSignatureService;
import eu.europa.esig.dss.utils.Utils;

public class JAdESLevelBDetachedTest extends AbstractJAdESTestSignature {

	private DocumentSignatureService<JAdESSignatureParameters, JAdESTimestampParameters> service;
	private DSSDocument documentToSign;
	private Date signingDate;

	@BeforeEach
	public void init() throws Exception {
		service = new JAdESService(getCompleteCertificateVerifier());
		service.setTspSource(getGoodTsa());
		documentToSign = new FileDocument(new File("src/test/resources/sample.json"));
		signingDate = new Date();
	}

	@Override
	protected JAdESSignatureParameters getSignatureParameters() {
		JAdESSignatureParameters signatureParameters = new JAdESSignatureParameters();
		signatureParameters.bLevel().setSigningDate(signingDate);
		signatureParameters.setSigningCertificate(getSigningCert());
		signatureParameters.setCertificateChain(getCertificateChain());
		signatureParameters.setSignaturePackaging(SignaturePackaging.DETACHED);
		signatureParameters.setSignatureLevel(SignatureLevel.JAdES_BASELINE_B);
		return signatureParameters;
	}
	
	@Override
	protected void onDocumentSigned(byte[] byteArray) {

		InMemoryDocument compactSignature = new InMemoryDocument(byteArray);
		JWSCompactSerializationParser parser = new JWSCompactSerializationParser(compactSignature);
		JWS jws = parser.parse();
		assertNotNull(jws);
		
		assertRequirementsValid(jws.getEncodedHeader());
		
		DSSDocument converted = JWSConverter.fromJWSCompactToJSONFlattenedSerialization(compactSignature);
		assertNotNull(converted);
		assertNotNull(converted.getMimeType());
		assertNotNull(converted.getName());

		verify(converted);

		converted = JWSConverter.fromJWSCompactToJSONSerialization(compactSignature);
		assertNotNull(converted);
		assertNotNull(converted.getMimeType());
		assertNotNull(converted.getName());

		verify(converted);
	}
	
	private void assertRequirementsValid(String encodedHeader) {
		try {
			String jsonString = new String(JAdESUtils.fromBase64Url(encodedHeader));
			Map<String, Object> protectedHeaderMap = JsonUtil.parseJson(jsonString);
			
			Object cty = protectedHeaderMap.get(HeaderParameterNames.CONTENT_TYPE);
			assertNull(cty);
			
			Map<?, ?> sigD = (Map<?, ?>) protectedHeaderMap.get(JAdESHeaderParameterNames.SIG_D);
			assertNotNull(sigD);
			
			Object mId = sigD.get(JAdESHeaderParameterNames.M_ID);
			assertNotNull(mId);
			
			Object hashM = sigD.get(JAdESHeaderParameterNames.HASH_M);
			assertNotNull(hashM);
			
			List<?> pars = (List<?>) sigD.get(JAdESHeaderParameterNames.PARS);
			assertTrue(Utils.isCollectionNotEmpty(pars));
			
			List<?> hashV = (List<?>) sigD.get(JAdESHeaderParameterNames.HASH_V);
			assertTrue(Utils.isCollectionNotEmpty(hashV));
			
			List<?> ctys = (List<?>) sigD.get(JAdESHeaderParameterNames.CTYS);
			assertTrue(Utils.isCollectionNotEmpty(ctys));
			
			assertEquals(pars.size(), hashV.size());
			assertEquals(pars.size(), ctys.size());
			
		} catch (JoseException e) {
			fail(e);
		}
	}

	@Override
	protected List<DSSDocument> getDetachedContents() {
		return Arrays.asList(documentToSign);
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
	protected String getSigningAlias() {
		return GOOD_USER;
	}

}
