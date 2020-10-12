package eu.europa.esig.dss.jades.requirements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.jose4j.json.JsonUtil;
import org.jose4j.jwx.HeaderParameterNames;
import org.junit.jupiter.api.BeforeEach;

import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.jades.JAdESSignatureParameters;
import eu.europa.esig.dss.jades.JAdESTimestampParameters;
import eu.europa.esig.dss.jades.DSSJsonUtils;
import eu.europa.esig.dss.jades.signature.AbstractJAdESTestSignature;
import eu.europa.esig.dss.jades.signature.JAdESService;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.FileDocument;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.signature.DocumentSignatureService;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.utils.Utils;

public abstract class AbstractJAdESRequirementsCheck extends AbstractJAdESTestSignature {
	
	private JAdESService service;
	private DSSDocument documentToSign;
	private JAdESSignatureParameters signatureParameters;
	
	@BeforeEach
	public void init() throws Exception {
		service = new JAdESService(getCompleteCertificateVerifier());
		service.setTspSource(getGoodTsa());
		
		documentToSign = new FileDocument(new File("src/test/resources/sample.json"));
		
		signatureParameters = new JAdESSignatureParameters();
		signatureParameters.bLevel().setSigningDate(new Date());
		signatureParameters.setSigningCertificate(getSigningCert());
		signatureParameters.setCertificateChain(getCertificateChain());
		signatureParameters.setSignaturePackaging(SignaturePackaging.ENVELOPING);
	}
	
	@Override
	protected void onDocumentSigned(byte[] byteArray)  {
		super.onDocumentSigned(byteArray);
		
		try {
			String payload = getPayload(byteArray);
			checkPayload(payload);
			
			String protectedHeader = getProtectedHeader(byteArray);
			checkProtectedHeader(protectedHeader);
			
			String signatureValue = getSignatureValue(byteArray);
			checkSignatureValue(signatureValue);
			
			Map<?, ?> unprotectedHeader = getUnprotectedHeader(byteArray);
			checkUnprotectedHeader(unprotectedHeader);
			
		} catch (Exception e) {
			fail(e);
		}
	}

	protected abstract String getPayload(byte[] byteArray) throws Exception;
	
	protected abstract String getProtectedHeader(byte[] byteArray) throws Exception;
	
	protected abstract String getSignatureValue(byte[] byteArray) throws Exception;
	
	protected abstract Map<?, ?> getUnprotectedHeader(byte[] byteArray) throws Exception;
	
	protected void checkPayload(String payload) {
		assertNotNull(payload);
		assertTrue(DSSJsonUtils.isBase64UrlEncoded(payload));
	}
	
	protected void checkProtectedHeader(String protectedHeader) throws Exception {
		assertNotNull(protectedHeader);
		assertTrue(DSSJsonUtils.isBase64UrlEncoded(protectedHeader));
		
		String jsonString = new String(DSSJsonUtils.fromBase64Url(protectedHeader));
		Map<String, Object> protectedHeaderMap = JsonUtil.parseJson(jsonString);
		
		checkSigningCertificate(protectedHeaderMap);
		checkCertificateChain(protectedHeaderMap);
		checkSigningTime(protectedHeaderMap);
		checkContentType(protectedHeaderMap);
		checkCrit(protectedHeaderMap);
	}

	protected void checkSigningCertificate(Map<?, ?> protectedHeaderMap) {
		Object x5tNS256 = protectedHeaderMap.get(HeaderParameterNames.X509_CERTIFICATE_SHA256_THUMBPRINT);
		Object x5tNo = protectedHeaderMap.get("x5t#o");
		assertTrue(x5tNS256 != null ^ x5tNo != null);
	}

	private void checkCertificateChain(Map<String, Object> protectedHeaderMap) {
		List<?> x5c = (List<?>) protectedHeaderMap.get(HeaderParameterNames.X509_CERTIFICATE_CHAIN);
		assertTrue(Utils.isCollectionNotEmpty(x5c));
		for (Object certObject : x5c) {
			assertNotNull(certObject);
			assertTrue(certObject instanceof String);
			CertificateToken certificateToken = DSSUtils.loadCertificateFromBase64EncodedString((String) certObject);
			assertNotNull(certificateToken);
		}
	}

	protected void checkSigningTime(Map<String, Object> protectedHeaderMap) throws Exception {
		String sigT = (String) protectedHeaderMap.get("sigT");
		assertNotNull(sigT);
		
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"); // RFC 3339
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		Date date = sdf.parse(sigT);
		assertNotNull(date);
	}

	protected void checkContentType(Map<String, Object> protectedHeaderMap) {
		Object cty = protectedHeaderMap.get(HeaderParameterNames.CONTENT_TYPE);
		Object sigD = protectedHeaderMap.get("sigD");
		assertTrue(cty != null ^ sigD != null);
	}

	private void checkCrit(Map<String, Object> protectedHeaderMap) {
		List<?> crit = (List<?>) protectedHeaderMap.get(HeaderParameterNames.CRITICAL);
		assertTrue(Utils.isCollectionNotEmpty(crit));
		
		List<String> excludedHeaders = Arrays.asList(HeaderParameterNames.AGREEMENT_PARTY_U_INFO, HeaderParameterNames.AGREEMENT_PARTY_V_INFO,
				HeaderParameterNames.ALGORITHM, HeaderParameterNames.AUTHENTICATION_TAG, HeaderParameterNames.CONTENT_TYPE, HeaderParameterNames.CRITICAL, 
				HeaderParameterNames.ENCRYPTION_METHOD, HeaderParameterNames.EPHEMERAL_PUBLIC_KEY, HeaderParameterNames.INITIALIZATION_VECTOR, 
				HeaderParameterNames.JWK, HeaderParameterNames.JWK_SET_URL, HeaderParameterNames.KEY_ID, HeaderParameterNames.PBES2_ITERATION_COUNT, 
				HeaderParameterNames.PBES2_SALT_INPUT, HeaderParameterNames.TYPE, HeaderParameterNames.X509_CERTIFICATE_CHAIN, 
				HeaderParameterNames.X509_CERTIFICATE_SHA256_THUMBPRINT, HeaderParameterNames.X509_CERTIFICATE_THUMBPRINT, HeaderParameterNames.X509_URL, 
				HeaderParameterNames.ZIP);
		
		List<String> includedHeaders = Arrays.asList(HeaderParameterNames.BASE64URL_ENCODE_PAYLOAD, "sigT", "x5t#o", "srCm", "sigPl", "srAts",
				"adoTst", "sigPld", "sigD");
		
		for (Object critEntry : crit) {
			assertNotNull(critEntry);
			assertTrue(critEntry instanceof String);
			assertTrue(!excludedHeaders.contains(critEntry));
			assertTrue(includedHeaders.contains(critEntry));
		}
	}

	protected void checkSignatureValue(String signatureValue) {
		assertNotNull(signatureValue);
		assertTrue(DSSJsonUtils.isBase64UrlEncoded(signatureValue));
	}
	
	protected void checkUnprotectedHeader(Map<?, ?> unprotectedHeaderMap) throws Exception {	
		checkSignatureTimestamp(unprotectedHeaderMap);
		checkCertificateValues(unprotectedHeaderMap);
		checkRevocationValues(unprotectedHeaderMap);
		checkCertificateReferences(unprotectedHeaderMap);
		checkRevocationReferences(unprotectedHeaderMap);
		checkRefTimestamps(unprotectedHeaderMap);
		checkArchiveTimestamp(unprotectedHeaderMap);
	}

	protected void checkSignatureTimestamp(Map<?, ?> unprotectedHeaderMap) {
		Map<?, ?> sigTst = (Map<?, ?>) getEtsiUElement(unprotectedHeaderMap, "sigTst");
		assertNotNull(sigTst);
		assertNull(sigTst.get("canonAlg"));
		List<?> tstokens = (List<?>) sigTst.get("tsTokens");
		assertNotNull(tstokens);
		assertEquals(1, tstokens.size());
	}

	protected void checkCertificateValues(Map<?, ?> unprotectedHeaderMap) {
		List<?> xVals = (List<?>) getEtsiUElement(unprotectedHeaderMap, "xVals");
		assertTrue(Utils.isCollectionNotEmpty(xVals));
		assertCertValsValid(xVals);
		
		List<?> axVals = (List<?>) getEtsiUElement(unprotectedHeaderMap, "axVals");
		if (axVals != null) {
			assertCertValsValid(axVals);
		}
	}
	
	private void assertCertValsValid(List<?> vals) {
		List<Object> pkiObjects = new ArrayList<>();
		for (Object xVal : vals) {
			assertNotNull(xVal);
			assertTrue(xVal instanceof Map<?, ?>);
			Object x509Cert = ((Map<?, ?>) xVal).get("x509Cert");
			assertNotNull(x509Cert);
			pkiObjects.add(x509Cert);
		}
		assertTrue(Utils.isCollectionNotEmpty(pkiObjects));
		assertNoDuplicatesFound(pkiObjects);
	}

	protected void checkRevocationValues(Map<?, ?> unprotectedHeaderMap) {
		Map<?, ?> rVals = (Map<?, ?>) getEtsiUElement(unprotectedHeaderMap, "rVals");
		assertNotNull(rVals);
		
		List<?> crlVals = (List<?>) rVals.get("crlVals");
		assertTrue(Utils.isCollectionNotEmpty(crlVals));
		assertNoDuplicatesFound(crlVals);
		
		List<?> ocspVals = (List<?>) rVals.get("ocspVals");
		assertTrue(Utils.isCollectionNotEmpty(ocspVals));
		assertNoDuplicatesFound(ocspVals);
		
		List<?> arVals = (List<?>) getEtsiUElement(unprotectedHeaderMap, "arVals");
		assertNull(arVals);
	}
	
	private void assertNoDuplicatesFound(List<?> pkiObjects) {
		List<String> valsList = new ArrayList<>();
		for (Object pkiOb : pkiObjects) {
			assertNotNull(pkiOb);
			assertTrue(pkiOb instanceof Map<?, ?>);
			Map<?, ?> pkiObMap = (Map<?, ?>) pkiOb;
			String val = (String) pkiObMap.get("val");
			assertNotNull(val);
			assertTrue(Utils.isBase64Encoded(val));
			assertTrue(!valsList.contains(val));
			valsList.add(val);
		}
	}

	protected void checkCertificateReferences(Map<?, ?> unprotectedHeaderMap) {
		Object xRefs = getEtsiUElement(unprotectedHeaderMap, "xRefs");
		assertNull(xRefs);
		Object axRefs = getEtsiUElement(unprotectedHeaderMap, "axRefs");
		assertNull(axRefs);
	}

	protected void checkRevocationReferences(Map<?, ?> unprotectedHeaderMap) {
		Object rRefs = getEtsiUElement(unprotectedHeaderMap, "rRefs");
		assertNull(rRefs);
		Object arRefs = getEtsiUElement(unprotectedHeaderMap, "arRefs");
		assertNull(arRefs);
	}
	
	protected void checkRefTimestamps(Map<?, ?> unprotectedHeaderMap) {
		Object sigRTst = getEtsiUElement(unprotectedHeaderMap, "sigRTst");
		assertNull(sigRTst);
		Object rfsTst = getEtsiUElement(unprotectedHeaderMap, "rfsTst");
		assertNull(rfsTst);
	}

	protected void checkArchiveTimestamp(Map<?, ?> unprotectedHeaderMap) {
		Map<?, ?> arcTst = (Map<?, ?>) getEtsiUElement(unprotectedHeaderMap, "arcTst");
		assertNotNull(arcTst);
		
		String timeStamped = (String) arcTst.get("timeStamped");
		assertNotNull(timeStamped);
		assertTrue("all".equals(timeStamped) || "previousArcTst".equals(timeStamped));
		
		Map<?, ?> tstContainer = (Map<?, ?>) arcTst.get("tstContainer");
		assertNotNull(tstContainer);
		
		List<?> tstokens = (List<?>) tstContainer.get("tsTokens");
		assertTrue(Utils.isCollectionNotEmpty(tstokens));
	}
	
	protected Object getEtsiUElement(Map<?, ?> unprotectedHeaderMap, String headerName) {
		List<?> etsiU = (List<?>) unprotectedHeaderMap.get("etsiU");
		for (Object etsiUItem : etsiU) {
			Object object = ((Map<?, ?>) etsiUItem).get(headerName);
			if (object != null) {
				return object;
			}
		}
		return null;
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
