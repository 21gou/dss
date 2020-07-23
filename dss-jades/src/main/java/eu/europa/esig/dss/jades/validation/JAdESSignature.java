package eu.europa.esig.dss.jades.validation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jose4j.jwx.HeaderParameterNames;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.DigestMatcherType;
import eu.europa.esig.dss.enumerations.EncryptionAlgorithm;
import eu.europa.esig.dss.enumerations.EndorsementType;
import eu.europa.esig.dss.enumerations.MaskGenerationFunction;
import eu.europa.esig.dss.enumerations.SigDMechanism;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureForm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.jades.HTTPHeader;
import eu.europa.esig.dss.jades.JAdESHeaderParameterNames;
import eu.europa.esig.dss.jades.JAdESUtils;
import eu.europa.esig.dss.jades.signature.HttpHeadersPayloadBuilder;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.model.Digest;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.spi.x509.CandidatesForSigningCertificate;
import eu.europa.esig.dss.spi.x509.CertificateValidity;
import eu.europa.esig.dss.spi.x509.revocation.crl.OfflineCRLSource;
import eu.europa.esig.dss.spi.x509.revocation.ocsp.OfflineOCSPSource;
import eu.europa.esig.dss.utils.Utils;
import eu.europa.esig.dss.validation.AdvancedSignature;
import eu.europa.esig.dss.validation.CommitmentTypeIndication;
import eu.europa.esig.dss.validation.DefaultAdvancedSignature;
import eu.europa.esig.dss.validation.ReferenceValidation;
import eu.europa.esig.dss.validation.SignatureCertificateSource;
import eu.europa.esig.dss.validation.SignatureCryptographicVerification;
import eu.europa.esig.dss.validation.SignatureDigestReference;
import eu.europa.esig.dss.validation.SignatureIdentifier;
import eu.europa.esig.dss.validation.SignaturePolicy;
import eu.europa.esig.dss.validation.SignaturePolicyProvider;
import eu.europa.esig.dss.validation.SignatureProductionPlace;
import eu.europa.esig.dss.validation.SignerRole;

public class JAdESSignature extends DefaultAdvancedSignature {

	private static final long serialVersionUID = -3730351687600398811L;

	private static final Logger LOG = LoggerFactory.getLogger(JAdESSignature.class);

	/** The JWS signature object */
	private final JWS jws;
	
	/** Defines if the validating signature is detached */
	private final boolean isDetached;

	public JAdESSignature(JWS jws) {
		this.jws = jws;
		this.isDetached = Utils.isArrayEmpty(jws.getUnverifiedPayloadBytes());
	}

	public JWS getJws() {
		return jws;
	}

	@Override
	public SignatureForm getSignatureForm() {
		return SignatureForm.JAdES;
	}

	@Override
	public SignatureAlgorithm getSignatureAlgorithm() {
		return SignatureAlgorithm.forJWA(jws.getAlgorithmHeaderValue());
	}

	@Override
	public EncryptionAlgorithm getEncryptionAlgorithm() {
		return getSignatureAlgorithm().getEncryptionAlgorithm();
	}

	@Override
	public DigestAlgorithm getDigestAlgorithm() {
		return getSignatureAlgorithm().getDigestAlgorithm();
	}

	@Override
	public MaskGenerationFunction getMaskGenerationFunction() {
		return getSignatureAlgorithm().getMaskGenerationFunction();
	}

	@Override
	public Date getSigningTime() {
		String signingTimeStr = jws.getHeaders().getStringHeaderValue(JAdESHeaderParameterNames.SIG_T);
		return JAdESUtils.getDate(signingTimeStr);
	}

	/**
	 * Checks if the JAdES Signature is a detached (contains 'sigD' dictionary)
	 * 
	 * @return TRUE if the signature is detached, FALSE otherwise
	 */
	public boolean isDetachedSignature() {
		return isDetached;
	}

	@Override
	public SignatureCertificateSource getCertificateSource() {
		if (offlineCertificateSource == null) {
			offlineCertificateSource = new JAdESCertificateSource(jws);
		}
		return offlineCertificateSource;
	}

	@Override
	public OfflineCRLSource getCRLSource() {
		if (signatureCRLSource == null) {
			signatureCRLSource = new JAdESCRLSource(jws);
		}
		return signatureCRLSource;
	}

	@Override
	public OfflineOCSPSource getOCSPSource() {
		if (signatureOCSPSource == null) {
			signatureOCSPSource = new JAdESOCSPSource(jws);
		}
		return signatureOCSPSource;
	}

	@Override
	public JAdESTimestampSource getTimestampSource() {
		if (signatureTimestampSource == null) {
			signatureTimestampSource = new JAdESTimestampSource(this);
		}
		return (JAdESTimestampSource) signatureTimestampSource;
	}

	@Override
	public SignatureProductionPlace getSignatureProductionPlace() {
		Map<?, ?> signaturePlace = (Map<?, ?>) jws.getHeaders()
				.getObjectHeaderValue(JAdESHeaderParameterNames.SIG_PL);
		if (signaturePlace != null) {
			SignatureProductionPlace result = new SignatureProductionPlace();
			result.setCity((String) signaturePlace.get(JAdESHeaderParameterNames.CITY));
			result.setStreetAddress((String) signaturePlace.get(JAdESHeaderParameterNames.STR_ADDR));
			result.setPostalCode((String) signaturePlace.get(JAdESHeaderParameterNames.POST_CODE));
			result.setStateOrProvince((String) signaturePlace.get(JAdESHeaderParameterNames.STAT_PROV));
			result.setCountryName((String) signaturePlace.get(JAdESHeaderParameterNames.COUNTRY));
			return result;
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<CommitmentTypeIndication> getCommitmentTypeIndications() {
		List<CommitmentTypeIndication> result = new ArrayList<>();
		Map<?, ?> signedCommitment = (Map<?, ?>) jws.getHeaders()
				.getObjectHeaderValue(JAdESHeaderParameterNames.SR_CM);
		if (signedCommitment != null) {
			Map<? ,?> commIdMap = (Map<? ,?>) signedCommitment.get(JAdESHeaderParameterNames.COMM_ID);
			String uri = (String) commIdMap.get(JAdESHeaderParameterNames.ID);
			if (Utils.isStringNotBlank(uri)) {
				CommitmentTypeIndication commitmentTypeIndication = new CommitmentTypeIndication(uri);
				commitmentTypeIndication.setDescription((String) commIdMap.get(JAdESHeaderParameterNames.DESC));
				commitmentTypeIndication.setDocumentReferences((List<String>) commIdMap.get(JAdESHeaderParameterNames.DOC_REFS));
				result.add(commitmentTypeIndication);
			} else {
				LOG.warn("Id parameter in the OID with the value '{}' is not conformant! The entry is skipped.", uri);
			}
		}
		return result;
	}

	@Override
	public String getContentType() {
		// TODO handle sigD
		return jws.getContentTypeHeaderValue();
	}

	@Override
	public String getMimeType() {
		return jws.getHeaders().getStringHeaderValue(HeaderParameterNames.TYPE);
	}

	@Override
	public String getContentIdentifier() {
		// not applicable
		return null;
	}

	@Override
	public String getContentHints() {
		// not applicable
		return null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<SignerRole> getClaimedSignerRoles() {
		List<SignerRole> result = new ArrayList<>();
		Map<?, ?> jsonMap = getSignerAttributes();
		if (jsonMap != null) {
			List<String> claimedList = (List<String>) jsonMap.get(JAdESHeaderParameterNames.CLAIMED);
			if (Utils.isCollectionNotEmpty(claimedList)) {
				for (String claimedBase64 : claimedList) {
					// TODO unclear standard
					String claimed = new String(Utils.fromBase64(claimedBase64));
					result.add(new SignerRole(claimed, EndorsementType.CLAIMED));
				}
			}
		}
		return result;
	}

	@Override
	public List<SignerRole> getCertifiedSignerRoles() {
		List<SignerRole> certifieds = new ArrayList<>();
		Map<?, ?> jsonMap = getSignerAttributes();
		if (jsonMap != null) {
			List<?> certified = (List<?>) jsonMap.get(JAdESHeaderParameterNames.CERTIFIED);
			if (Utils.isCollectionNotEmpty(certified)) {
				// TODO unclear standard
				LOG.info("Attribute {} is detected", JAdESHeaderParameterNames.CERTIFIED);
			}
		}
		return certifieds;
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<SignerRole> getSignedAssertions() {
		List<SignerRole> result = new ArrayList<>();
		Map<?, ?> jsonMap = getSignerAttributes();
		if (jsonMap != null) {
			List<String> signedAssertionsList = (List<String>) jsonMap.get(JAdESHeaderParameterNames.SIGNED_ASSERTIONS);
			if (Utils.isCollectionNotEmpty(signedAssertionsList)) {
				for (String signedAssertionBase64 : signedAssertionsList) {
					String signedAssertion = new String(Utils.fromBase64(signedAssertionBase64));
					result.add(new SignerRole(signedAssertion, EndorsementType.SIGNED));
				}
			}
		}
		return result;
	}

	private Map<?, ?> getSignerAttributes() {
		return (Map<?, ?>) jws.getHeaders().getObjectHeaderValue(JAdESHeaderParameterNames.SR_ATS);
	}

	@Override
	public List<AdvancedSignature> getCounterSignatures() {
		// not supported
		return Collections.emptyList();
	}

	@Override
	public String getDAIdentifier() {
		// not applicable for JAdES
		return null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void checkSignaturePolicy(SignaturePolicyProvider signaturePolicyDetector) {
		Map<String, Object> sigPolicy = (Map<String, Object>) jws.getHeaders().getObjectHeaderValue(JAdESHeaderParameterNames.SIG_PID);
		if (Utils.isMapNotEmpty(sigPolicy)) {
			Map<String, Object> policyId = (Map<String, Object>) sigPolicy.get(JAdESHeaderParameterNames.ID);
			String id = (String) policyId.get(JAdESHeaderParameterNames.ID);

			signaturePolicy = new SignaturePolicy(DSSUtils.getOidCode(id));
			signaturePolicy.setDescription((String) policyId.get(JAdESHeaderParameterNames.DESC));
			signaturePolicy.setDigest(JAdESUtils.getDigest((Map<?, ?>) sigPolicy.get(JAdESHeaderParameterNames.HASH_AV)));

			List<Object> qualifiers = (List<Object>) sigPolicy.get(JAdESHeaderParameterNames.SIG_PQUALS);
			if (Utils.isCollectionNotEmpty(qualifiers)) {
				signaturePolicy.setUrl(getSPUri(qualifiers));
			}
		}
	}

	@SuppressWarnings("unchecked")
	private String getSPUri(List<Object> qualifiers) {
		for (Object qualifier : qualifiers) {
			Map<String, Object> qualiferMap = (Map<String, Object>) qualifier;
			String spUri = (String)qualiferMap.get(JAdESHeaderParameterNames.SP_URI);
			if (Utils.isStringNotEmpty(spUri)) {
				return spUri;
			}
		}
		return null;
	}

	@Override
	public byte[] getSignatureValue() {
		return jws.getSignatureValue();
	}

	// TODO : no definition available in ETSI TS 119 442 - V1.1.1
	@Override
	public SignatureDigestReference getSignatureDigestReference(DigestAlgorithm digestAlgorithm) {
		String encodedHeader = jws.getEncodedHeader();
		String payload = jws.isRfc7797UnencodedPayload() ? jws.getUnverifiedPayload() : jws.getEncodedPayload();
		String encodedSignature = jws.getEncodedSignature();
		byte[] signatureReferenceBytes = JAdESUtils.concatenate(encodedHeader, payload, encodedSignature).getBytes();
		byte[] digestValue = DSSUtils.digest(digestAlgorithm, signatureReferenceBytes);
		return new SignatureDigestReference(new Digest(digestAlgorithm, digestValue));
	}
	
	@Override
	public Digest getDataToBeSignedRepresentation() {
		List<ReferenceValidation> referenceValidations = getReferenceValidations();
		for (ReferenceValidation referenceValidation : referenceValidations) {
			if (DigestMatcherType.JWS_SIGNING_INPUT_DIGEST.equals(referenceValidation.getType())) {
				return referenceValidation.getDigest();
			}
		}
		// shall not happen
		throw new DSSException("JWS_SIGNING_INPUT_DIGEST is not found! Unable to compute DTBSR.");
	}

	@Override
	protected SignatureIdentifier buildSignatureIdentifier() {
		return new JAdESSignatureIdentifier(this);
	}

	@Override
	public void checkSignatureIntegrity() {

		if (signatureCryptographicVerification != null) {
			return;
		}
		
		signatureCryptographicVerification = new SignatureCryptographicVerification();

		boolean refsFound = false;
		boolean refsIntact = false;
		
		List<ReferenceValidation> referenceValidations = getReferenceValidations();
		
		if (Utils.isCollectionNotEmpty(referenceValidations)) {
			refsFound = true;
			refsIntact = true;
			
			for (ReferenceValidation referenceValidation : referenceValidations) {
				if (DigestMatcherType.JWS_SIGNING_INPUT_DIGEST.equals(referenceValidation.getType())) {
					JAdESReferenceValidation signingInputReferenceValidation = (JAdESReferenceValidation) referenceValidation;
					signatureCryptographicVerification.setSignatureIntact(signingInputReferenceValidation.isIntact());
					
					for (String errorMessage : signingInputReferenceValidation.getErrorMessages()) {
						signatureCryptographicVerification.setErrorMessage(errorMessage);
					}
				}
				refsFound = refsFound && referenceValidation.isFound();
				refsIntact = refsIntact && referenceValidation.isIntact();
			}
		}
		
		signatureCryptographicVerification.setReferenceDataFound(refsFound);
		signatureCryptographicVerification.setReferenceDataIntact(refsIntact);

	}

	@Override
	public List<ReferenceValidation> getReferenceValidations() {
		if (referenceValidations == null) {
			referenceValidations = new ArrayList<>();
			
			JAdESReferenceValidation signingInputReferenceValidation = getSigningInputReferenceValidation();
			referenceValidations.add(signingInputReferenceValidation);
			
			List<JAdESReferenceValidation> detachedReferenceValidations = getDetachedReferenceValidations();
			if (Utils.isCollectionNotEmpty(detachedReferenceValidations)) {
				referenceValidations.addAll(detachedReferenceValidations);
			}
			
		}
		return referenceValidations;
	}
	
	private JAdESReferenceValidation getSigningInputReferenceValidation() {
		JAdESReferenceValidation signatureValueReferenceValidation = new JAdESReferenceValidation();
		signatureValueReferenceValidation.setType(DigestMatcherType.JWS_SIGNING_INPUT_DIGEST);
		
		try {
			SignatureAlgorithm signatureAlgorithm = getSignatureAlgorithm();
			if (signatureAlgorithm != null) {
				
				String encodedHeader = jws.getEncodedHeader();
				if (Utils.isStringNotEmpty(encodedHeader)) {
					
					SigDMechanism sigDMechanism = getSigDMechanism();
					boolean detachedContentPresent = Utils.isCollectionNotEmpty(detachedContents);
					if (!isDetachedSignature()) {
						// not detached
						signatureValueReferenceValidation.setFound(true);
						
					} else if (sigDMechanism == null && detachedContentPresent) {
						// simple detached signature
						signatureValueReferenceValidation.setFound(detachedContents.size() == 1);
						jws.setDetachedPayload(DSSUtils.toByteArray(detachedContents.get(0)));
						
					} else if (SigDMechanism.HTTP_HEADERS.equals(getSigDMechanism())) {
						// detached with HTTP_HEADERS mechanism
						signatureValueReferenceValidation.setFound(detachedContentPresent);
						jws.setDetachedPayload(getPayloadForHttpHeadersMechanism());
						
					} else if (SigDMechanism.OBJECT_ID_BY_URI.equals(getSigDMechanism())) {
						// detached with OBJECT_ID_BY_URI mechanism
						signatureValueReferenceValidation.setFound(detachedContentPresent);
						jws.setDetachedPayload(getPayloadForObjectIdByUriMechanism());
						
					} else if (SigDMechanism.OBJECT_ID_BY_URI_HASH.equals(getSigDMechanism())) {
						// the sigD itself is signed with OBJECT_ID_BY_URI_HASH mechanism
						signatureValueReferenceValidation.setFound(true);
						
					} else {
						// otherwise original content is not found
						LOG.warn("The payload is not found! The detached content must be provided!");
						
					}
					
					String payload = jws.getSignedPayload();
					String headerAndPayloadResult = JAdESUtils.concatenate(encodedHeader, payload);
					// The data to sign by RFC 7515 shall be ASCII-encoded
					byte[] dataToSign = JAdESUtils.getAsciiBytes(headerAndPayloadResult);
					DigestAlgorithm digestAlgorithm = signatureAlgorithm.getDigestAlgorithm();
					Digest digest = new Digest(digestAlgorithm, DSSUtils.digest(digestAlgorithm, dataToSign));
					signatureValueReferenceValidation.setDigest(digest);
					
					boolean coreValidity = false;
	
					CandidatesForSigningCertificate candidates = getCandidatesForSigningCertificate();
					if (candidates.isEmpty()) {
						signatureValueReferenceValidation
								.addErrorMessage("There is no signing certificate within the signature or certificate pool.");
					}
	
					jws.setKnownCriticalHeaders(JAdESUtils.getSupportedCriticalHeaders());
					jws.setDoKeyValidation(false); // restrict on key size,...
	
					LOG.debug("Determining signing certificate from certificate candidates list...");
					final List<String> preliminaryErrorMessages = new ArrayList<>();
					int certificateNumber = 0;
					for (CertificateValidity certificateValidity : candidates.getCertificateValidityList()) {
						String errorMessagePrefix = "Certificate #" + (certificateNumber + 1) + ": ";
	
						jws.setKey(certificateValidity.getPublicKey());
	
						try {
							coreValidity = jws.verifySignature();
							if (coreValidity) {
								LOG.info("Determining signing certificate from certificate candidates list succeeded");
								candidates.setTheCertificateValidity(certificateValidity);
								break;
							}
						} catch (JoseException e) {
							LOG.debug("Exception while probing candidate certificate as signing certificate: {}", e.getMessage());
							preliminaryErrorMessages.add(errorMessagePrefix + e.getMessage());
						}
	
						certificateNumber++;
					}
	
					if (!coreValidity) {
						LOG.warn("Determining signing certificate from certificate candidates list failed: {}",
								preliminaryErrorMessages);
						for (String preliminaryErrorMessage : preliminaryErrorMessages) {
							signatureValueReferenceValidation.addErrorMessage(preliminaryErrorMessage);
						}
					}
	
					signatureValueReferenceValidation.setIntact(coreValidity);
					
				}
			}
		} catch (DSSException e) {
			LOG.error("The validation of signed input failed! Reason : {}", e.getMessage());
		}
		
		return signatureValueReferenceValidation;
	}
	
	public String getKid() {
		return jws.getKeyIdHeaderValue();
	}

	private List<JAdESReferenceValidation> getDetachedReferenceValidations() {
		if (isDetachedSignature()) {
			SigDMechanism sigDMechanism = getSigDMechanism();
			if (sigDMechanism != null) {
				switch (sigDMechanism) {
					case HTTP_HEADERS:
					case OBJECT_ID_BY_URI:
						// the documents are added to the payload, not possible to extract separate reference validations
						break;
					case OBJECT_ID_BY_URI_HASH:
						return getReferenceValidationsByUriHashMechanism();
					default:
						LOG.warn("The SigDMechanism '{}' is not supported!", sigDMechanism);
						break;
				}
			}
		}
		return Collections.emptyList();
	}
	
	/**
	 * Returns a mechanism used in 'sigD' to cover a detached content
	 * 
	 * @return {@link SigDMechanism}
	 */
	public SigDMechanism getSigDMechanism() {
		Map<?, ?> signatureDetached = (Map<?, ?>) jws.getHeaders()
				.getObjectHeaderValue(JAdESHeaderParameterNames.SIG_D);
		if (signatureDetached != null) {
			String mechanismUri = (String) signatureDetached.get(JAdESHeaderParameterNames.M_ID);
			SigDMechanism sigDMechanism = SigDMechanism.forUri(mechanismUri);
			if (sigDMechanism == null) {
				LOG.error("The sigDMechanism with uri '{}' is not supported!", mechanismUri);
			}
			return sigDMechanism;
		}
		return null;
	}
	
	private byte[] getPayloadForHttpHeadersMechanism() {
		if (Utils.isCollectionEmpty(detachedContents)) {
			throw new DSSException("The detached contents shall be provided for validating a detached signature!");
		}
		
		/*
		 * Case-insensitive, see TS 119 182-1 "5.2.8.2	Mechanism HttpHeaders":
		 * 
		 * For this referencing mechanism, the contents of the pars member shall be 
		 * an array of lowercased names of HTTP header fields, each one with the semantics 
		 * and syntax specified in clause 2.1.3 of draft-cavage-http-signatures-10: 
		 * "Signing HTTP Messages" [17].
		 */
		List<DSSDocument> documentsByUri = getSignedDocumentsByUri(false);
		List<HTTPHeader> httpHeaders = JAdESUtils.toHTTPHeaders(documentsByUri);
		HttpHeadersPayloadBuilder httpHeadersPayloadBuilder = new HttpHeadersPayloadBuilder(httpHeaders);
		
		return httpHeadersPayloadBuilder.build();
	}
	
	private byte[] getPayloadForObjectIdByUriMechanism() {
		if (Utils.isCollectionEmpty(detachedContents)) {
			throw new DSSException("The detached contents shall be provided for validating a detached signature!");
		}
		
		List<DSSDocument> signedDocumentsByUri = getSignedDocumentsByUri(true);
		
		if (Utils.isCollectionEmpty(signedDocumentsByUri)) {
			throw new DSSException("The matching detached content has not been found!");
		} else if (signedDocumentsByUri.size() == 1) {
			return DSSUtils.toByteArray(signedDocumentsByUri.get(0));
		} else {
			try {
				return JAdESUtils.concatenateDSSDocuments(signedDocumentsByUri);
			} catch (IOException e) {
				throw new DSSException(String.format("Unable to build a payload for detached signature with ObjectIdByURI mechanism. "
						+ "Reason : %s", e.getMessage()), e); 
			}
		}
	}
	
	/**
	 * Returns a list of signed documents by the list of URIs present in 'sigD'
	 * Keeps the original order according to 'pars' dictionary content
	 * Used in ObjectByUri detached signature mechanism
	 * 
	 * @param caseSensitive defines if the name value is case-sensitive
	 * @return a list of {@link DSSDocument}s
	 */
	public List<DSSDocument> getSignedDocumentsByUri(boolean caseSensitive) {
		List<String> signedDataUriList = getSignedDataUriList();
		
		if (Utils.isCollectionEmpty(detachedContents)) {
			LOG.warn("Detached contents is not provided!");
			return Collections.emptyList();
		}
		
		if (signedDataUriList.size() == 1 && detachedContents.size() == 1) {
			return detachedContents;
		}
		
		List<DSSDocument> signedDocuments = new ArrayList<>();
		for (String signedDataName : signedDataUriList) {
			boolean found = false;
			for (DSSDocument document : detachedContents) {
				if (Utils.areStringsEqual(signedDataName, document.getName()) || 
						!caseSensitive && Utils.areStringsEqualIgnoreCase(signedDataName, document.getName())) {
					found = true;
					signedDocuments.add(document);
					// do not break - same name docs possible
				}
			}
			if (!found) {
				LOG.warn("The detached content for a signed data with name '{}' has not been found!", signedDataName);
			}
		}
		
		return signedDocuments;
	}
	
	private List<JAdESReferenceValidation> getReferenceValidationsByUriHashMechanism() {
		List<DSSDocument> detachedDocuments = detachedContents;
		
		if (Utils.isCollectionEmpty(detachedContents)) {
			LOG.warn("The detached content is not provided! Validation of 'sigD' is not possible.");
			detachedDocuments = Collections.emptyList();
			// continue in order to extract signed data references
		}
		
		DigestAlgorithm digestAlgorithm = getDigestAlgorithmForDetachedContent();
		if (digestAlgorithm == null) {
			LOG.warn("The DigestAlgorithm has not been found for detached content.");
			return Collections.emptyList();
		}
		
		Map<String, String> signedDataHashMap = getSignedDataUriHashMap();
		if (Utils.isMapEmpty(signedDataHashMap)) {
			LOG.warn("The SignedData has not been found or incorrect for detached content.");
			return Collections.emptyList();
		}
		
		List<JAdESReferenceValidation> detachedReferenceValidations = new ArrayList<>();

		for (Map.Entry<String, String> signedDataEntry : signedDataHashMap.entrySet()) {
			JAdESReferenceValidation referenceValidation = new JAdESReferenceValidation();
			referenceValidation.setType(DigestMatcherType.SIG_D_ENTRY);
			
			String signedDataName = signedDataEntry.getKey();
			referenceValidation.setName(signedDataName);
			
			String expectedDigest = signedDataEntry.getValue();
			referenceValidation.setDigest(new Digest(digestAlgorithm, Utils.fromBase64(expectedDigest)));
			
			boolean found = false;
			// accept document with any name if only one detached document has been signed
			if (signedDataHashMap.size() == 1 && detachedDocuments.size() == 1) {
				found = true;
				if (isDocumentDigestMatch(detachedDocuments.get(0), digestAlgorithm, expectedDigest)) {
					referenceValidation.setIntact(true);
				}
				
			} else {
				// if more than one document signed/provided
				for (DSSDocument detachedDocument : detachedDocuments) {
					if (signedDataName.equals(detachedDocument.getName())) {
						found = true;
						if (isDocumentDigestMatch(detachedDocument, digestAlgorithm, expectedDigest)) {
							referenceValidation.setIntact(true);
						}
					}
				}
			}
			
			referenceValidation.setFound(found);
			if (!found) {
				LOG.warn("A valid detached document for a 'sigD' entry with name '{}' has not been found!", signedDataName);
			}
			
			detachedReferenceValidations.add(referenceValidation);
		}
		
		if (Utils.isCollectionEmpty(detachedReferenceValidations)) {
			// add an empty reference if none found
			JAdESReferenceValidation referenceValidation = new JAdESReferenceValidation();
			referenceValidation.setType(DigestMatcherType.SIG_D_ENTRY);
			detachedReferenceValidations.add(referenceValidation);
		}
		
		return detachedReferenceValidations;
	}
	
	private DigestAlgorithm getDigestAlgorithmForDetachedContent() {
		Map<?, ?> signatureDetached = (Map<?, ?>) jws.getHeaders()
				.getObjectHeaderValue(JAdESHeaderParameterNames.SIG_D);
		if (signatureDetached != null) {
			String digestAlgoUri = (String) signatureDetached.get(JAdESHeaderParameterNames.HASH_M);
			return DigestAlgorithm.forXML(digestAlgoUri);
		}
		return null;
	}
	
	private Map<String, String> getSignedDataUriHashMap() {
		Map<String, String> signedDataHashMap = new LinkedHashMap<>(); // LinkedHashMap is used to keep the original order
		
		List<String> signedDataUriList = getSignedDataUriList();
		List<String> signedDataHashList = getSignedDataHashList();
		if (signedDataUriList.size() != signedDataHashList.size()) {
			LOG.warn("The size of 'pars' and 'hashV' dictionaries does not match! See '5.2.8 The sigD header parameter'.");
			return signedDataHashMap;
		}
		
		for (int ii = 0; ii < signedDataUriList.size(); ii++) {
			signedDataHashMap.put(signedDataUriList.get(ii), signedDataHashList.get(ii));
		}
		return signedDataHashMap;
	}
	
	@SuppressWarnings("unchecked")
	private List<String> getSignedDataUriList() {
		Map<?, ?> signatureDetached = (Map<?, ?>) jws.getHeaders()
				.getObjectHeaderValue(JAdESHeaderParameterNames.SIG_D);
		if (signatureDetached != null) {
			return (List<String>) signatureDetached.get(JAdESHeaderParameterNames.PARS);
		}
		return Collections.emptyList();
	}
	
	@SuppressWarnings("unchecked")
	private List<String> getSignedDataHashList() {
		Map<?, ?> signatureDetached = (Map<?, ?>) jws.getHeaders()
				.getObjectHeaderValue(JAdESHeaderParameterNames.SIG_D);
		if (signatureDetached != null) {
			return (List<String>) signatureDetached.get(JAdESHeaderParameterNames.HASH_V);
		}
		return Collections.emptyList();
	}
	
	private boolean isDocumentDigestMatch(DSSDocument document, DigestAlgorithm digestAlgorithm, String expectedDigest) {
		String computedDigest = document.getDigest(digestAlgorithm);
		if (expectedDigest.equals(computedDigest)) {
			return true;
		}
		LOG.warn("The computed digest '{}' from a document with name '{}' does not match one provided on the sigD : {}!", 
				computedDigest, document.getName(), expectedDigest);
		return false;
	}

	public List<DSSDocument> getOriginalDocuments() {
		if (isDetachedSignature()) {
			
			List<DSSDocument> originalDocuments = new ArrayList<>();
			
			List<ReferenceValidation> referenceValidations = getReferenceValidations();
			for (ReferenceValidation referenceValidation : referenceValidations) {
				if (DigestMatcherType.SIG_D_ENTRY.equals(referenceValidation.getType()) && referenceValidation.isIntact()) {
					for (DSSDocument detachedDocument : detachedContents) {
						if (referenceValidation.getName().equals(detachedDocument.getName())) {
							originalDocuments.add(detachedDocument);
						}
					} 
				}
			}
			
			if (Utils.isCollectionEmpty(originalDocuments)) {
				// check if the signature of an old detached format
				SignatureCryptographicVerification signatureCryptographicVerification = getSignatureCryptographicVerification();
				if (signatureCryptographicVerification.isSignatureIntact()) {
					if (Utils.isCollectionNotEmpty(detachedContents) && detachedContents.size() == 1) {
						return Collections.singletonList(detachedContents.get(0));
						
					} else if (SigDMechanism.HTTP_HEADERS.equals(getSigDMechanism())) {
						return getSignedDocumentsByUri(false);
								
					} else if (SigDMechanism.OBJECT_ID_BY_URI.equals(getSigDMechanism())) {
						return getSignedDocumentsByUri(true);
								
					}
				} 
			}
			
			return originalDocuments;
			
		} else {
			byte[] payloadBytes = jws.getUnverifiedPayloadBytes();
			return Collections.singletonList(new InMemoryDocument(payloadBytes));
		}
	}

	@Override
	public SignatureLevel getDataFoundUpToLevel() {
		if (!hasBProfile()) {
			return SignatureLevel.JSON_NOT_ETSI;
		}
		if (!hasTProfile()) {
			return SignatureLevel.JAdES_BASELINE_B;
		}
		if (hasLTProfile()) {
			if (hasLTAProfile()) {
				return SignatureLevel.JAdES_BASELINE_LTA;
			}
			return SignatureLevel.JAdES_BASELINE_LT;
		}
		return SignatureLevel.JAdES_BASELINE_T;
	}

	private boolean hasBProfile() {
		return getSigningTime() != null && getSignatureAlgorithm() != null;
	}

}
