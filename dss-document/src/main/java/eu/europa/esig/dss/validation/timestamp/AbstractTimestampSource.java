/**
 * DSS - Digital Signature Services
 * Copyright (C) 2015 European Commission, provided under the CEF programme
 * 
 * This file is part of the "DSS - Digital Signature Services" project.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package eu.europa.esig.dss.validation.timestamp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.europa.esig.dss.enumerations.ArchiveTimestampType;
import eu.europa.esig.dss.enumerations.TimestampType;
import eu.europa.esig.dss.enumerations.TimestampedObjectType;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.model.identifier.EncapsulatedRevocationTokenIdentifier;
import eu.europa.esig.dss.model.identifier.Identifier;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.model.x509.revocation.crl.CRL;
import eu.europa.esig.dss.model.x509.revocation.ocsp.OCSP;
import eu.europa.esig.dss.spi.x509.CertificateRef;
import eu.europa.esig.dss.spi.x509.ListCertificateSource;
import eu.europa.esig.dss.spi.x509.revocation.crl.CRLRef;
import eu.europa.esig.dss.spi.x509.revocation.ocsp.OCSPRef;
import eu.europa.esig.dss.utils.Utils;
import eu.europa.esig.dss.validation.AdvancedSignature;
import eu.europa.esig.dss.validation.DefaultAdvancedSignature;
import eu.europa.esig.dss.validation.ISignatureAttribute;
import eu.europa.esig.dss.validation.ListRevocationSource;
import eu.europa.esig.dss.validation.SignatureCertificateSource;
import eu.europa.esig.dss.validation.SignatureProperties;
import eu.europa.esig.dss.validation.scope.SignatureScope;

/**
 * Contains a set of {@link TimestampToken}s found in a {@link DefaultAdvancedSignature} object
 */
@SuppressWarnings("serial")
public abstract class AbstractTimestampSource<AS extends AdvancedSignature, SignatureAttribute extends ISignatureAttribute> implements TimestampSource {

	private static final Logger LOG = LoggerFactory.getLogger(AbstractTimestampSource.class);
	
	/**
	 * The signature is being validated
	 */
	protected final AS signature;
	
	/**
	 * Revocation sources containing merged data from signature and timestamps
	 */
	protected ListRevocationSource<CRL> crlSource;
	protected ListRevocationSource<OCSP> ocspSource;
	
	/**
	 * CertificateSource containing merged data from signature and timestamps
	 */
	protected ListCertificateSource certificateSource;
	
	// Enclosed content timestamps.
	protected List<TimestampToken> contentTimestamps;

	// Enclosed signature timestamps.
	protected List<TimestampToken> signatureTimestamps;

	// Enclosed SignAndRefs timestamps.
	protected List<TimestampToken> sigAndRefsTimestamps;

	// Enclosed RefsOnly timestamps.
	protected List<TimestampToken> refsOnlyTimestamps;

	// This variable contains the list of enclosed archive signature timestamps.
	protected List<TimestampToken> archiveTimestamps;
	
	// A list of all TimestampedReferences extracted from a signature
	protected List<TimestampedReference> encapsulatedReferences;

	/**
	 * Default constructor
	 * 
	 * @param signature {@link AdvancedSignature} is being validated
	 */
	protected AbstractTimestampSource(final AS signature) {
		Objects.requireNonNull(signature, "The signature cannot be null!");
		this.signature = signature;
	}
	
	@Override
	public List<TimestampToken> getContentTimestamps() {
		if (contentTimestamps == null) {
			createAndValidate();
		}
		return contentTimestamps;
	}
	
	@Override
	public List<TimestampToken> getSignatureTimestamps() {
		if (signatureTimestamps == null) {
			createAndValidate();
		}
		return signatureTimestamps;
	}
	
	@Override
	public List<TimestampToken> getTimestampsX1() {
		if (sigAndRefsTimestamps == null) {
			createAndValidate();
		}
		return sigAndRefsTimestamps;
	}
	
	@Override
	public List<TimestampToken> getTimestampsX2() {
		if (refsOnlyTimestamps == null) {
			createAndValidate();
		}
		return refsOnlyTimestamps;
	}
	
	@Override
	public List<TimestampToken> getArchiveTimestamps() {
		if (archiveTimestamps == null) {
			createAndValidate();
		}
		return archiveTimestamps;
	}
	
	@Override
	public List<TimestampToken> getDocumentTimestamps() {
		/** Applicable only for PAdES */
		return Collections.emptyList();
	}
	
	@Override
	public List<TimestampToken> getAllTimestamps() {
		List<TimestampToken> timestampTokens = new ArrayList<>();
		timestampTokens.addAll(getContentTimestamps());
		timestampTokens.addAll(getSignatureTimestamps());
		timestampTokens.addAll(getTimestampsX1());
		timestampTokens.addAll(getTimestampsX2());
		timestampTokens.addAll(getArchiveTimestamps());
		return timestampTokens;
	}
	
	@Override
	public ListCertificateSource getTimestampCertificateSources() {
		ListCertificateSource result = new ListCertificateSource();
		for (TimestampToken timestampToken : getAllTimestamps()) {
			result.add(timestampToken.getCertificateSource());
		}
		return result;
	}
	
	@Override
	public ListCertificateSource getTimestampCertificateSourcesExceptLastArchiveTimestamp() {
		ListCertificateSource result = new ListCertificateSource();

		for (final TimestampToken timestampToken : getContentTimestamps()) {
			result.add(timestampToken.getCertificateSource());
		}
		for (final TimestampToken timestampToken : getTimestampsX1()) {
			result.add(timestampToken.getCertificateSource());
		}
		for (final TimestampToken timestampToken : getTimestampsX2()) {
			result.add(timestampToken.getCertificateSource());
		}
		for (final TimestampToken timestampToken : getSignatureTimestamps()) {
			result.add(timestampToken.getCertificateSource());
		}

		List<TimestampToken> archiveTsps = getArchiveTimestamps();
		int archiveTimestampsSize = archiveTsps.size();
		if (archiveTimestampsSize > 0) {
			archiveTimestampsSize--;
		}
		for (int ii = 0; ii < archiveTimestampsSize; ii++) {
			TimestampToken timestampToken = archiveTsps.get(ii);
			result.add(timestampToken.getCertificateSource());
		}

		return result;
	}

	@Override
	public ListRevocationSource<CRL> getTimestampCRLSources() {
		ListRevocationSource<CRL> result = new ListRevocationSource<CRL>();
		for (TimestampToken timestampToken : getAllTimestamps()) {
			result.add(timestampToken.getCRLSource());
		}
		return result;
	}

	@Override
	public ListRevocationSource<OCSP> getTimestampOCSPSources() {
		ListRevocationSource<OCSP> result = new ListRevocationSource<OCSP>();
		for (TimestampToken timestampToken : getAllTimestamps()) {
			result.add(timestampToken.getOCSPSource());
		}
		return result;
	}
	
	@Override
	public List<TimestampedReference> getEncapsulatedReferences() {
		if (encapsulatedReferences == null) {
			createAndValidate();
		}
		return encapsulatedReferences;
	}
	
	/**
	 * Creates and validates all timestamps
	 * Must be called only once
	 */
	protected void createAndValidate() {
		makeTimestampTokens();
		validateTimestamps();
	}

	@Override
	public void addExternalTimestamp(TimestampToken timestamp) {
		// if timestamp tokens not created yet
		if (archiveTimestamps == null) {
			createAndValidate();
		}
		processExternalTimestamp(timestamp);
		if (TimestampType.ARCHIVE_TIMESTAMP == timestamp.getTimeStampType()) {
			archiveTimestamps.add(timestamp);
		} else {
			throw new DSSException(
					String.format("The signature timestamp source does not support timestamp tokens with type [%s]. " + "The TimestampToken was not added.",
							timestamp.getTimeStampType().name()));
		}
	}
	
	/**
	 * Populates all the lists by data found into the signature
	 */
	protected void makeTimestampTokens() {
		
		// initialize timestamp lists
		contentTimestamps = new ArrayList<>();
		signatureTimestamps = new ArrayList<>();
		sigAndRefsTimestamps = new ArrayList<>();
		refsOnlyTimestamps = new ArrayList<>();
		archiveTimestamps = new ArrayList<>();
		
		// initialize combined revocation sources
		crlSource = new ListRevocationSource<CRL>(signature.getCRLSource());
		ocspSource = new ListRevocationSource<OCSP>(signature.getOCSPSource());
		certificateSource = new ListCertificateSource(signature.getCertificateSource());
		
		// a list of all embedded references
		encapsulatedReferences = new ArrayList<TimestampedReference>();
		
		final SignatureProperties<SignatureAttribute> signedSignatureProperties = getSignedSignatureProperties();
		
		final List<SignatureAttribute> signedAttributes = signedSignatureProperties.getAttributes();
		for (SignatureAttribute signedAttribute : signedAttributes) {
			
			List<TimestampToken> timestampTokens;
			
			if (isContentTimestamp(signedAttribute)) {
				timestampTokens = makeTimestampTokens(signedAttribute, TimestampType.CONTENT_TIMESTAMP, getAllSignedDataReferences());
				if (Utils.isCollectionEmpty(timestampTokens)) {
					continue;
				}
				
			} else if (isAllDataObjectsTimestamp(signedAttribute)) {
				timestampTokens = makeTimestampTokens(signedAttribute, TimestampType.ALL_DATA_OBJECTS_TIMESTAMP, getAllSignedDataReferences());
				if (Utils.isCollectionEmpty(timestampTokens)) {
					continue;
				}
				
			} else if (isIndividualDataObjectsTimestamp(signedAttribute)) {				
				List<TimestampedReference> references = getIndividualContentTimestampedReferences(signedAttribute);
				timestampTokens = makeTimestampTokens(signedAttribute, TimestampType.INDIVIDUAL_DATA_OBJECTS_TIMESTAMP, references);
				if (Utils.isCollectionEmpty(timestampTokens)) {
					continue;
				}
				
			} else {
				continue;
				
			}
			populateSources(timestampTokens);
			contentTimestamps.addAll(timestampTokens);
		}
		
		
		final SignatureProperties<SignatureAttribute> unsignedSignatureProperties = getUnsignedSignatureProperties();
		if (unsignedSignatureProperties == null || !unsignedSignatureProperties.isExist()) {
			// timestamp tokens cannot be created if signature does not contain "unsigned-signature-properties" element
			return;
		}
		
		final List<TimestampToken> timestamps = new ArrayList<>();
		
		// JAdES specific (contains references to the last 'arcTst' and the associated 'tstVd')
		List<TimestampedReference> previousArcTstReferences = new ArrayList<>();
		
		final List<SignatureAttribute> unsignedAttributes = unsignedSignatureProperties.getAttributes();
		for (SignatureAttribute unsignedAttribute : unsignedAttributes) {
			
			List<TimestampToken> timestampTokens;
			
			if (isSignatureTimestamp(unsignedAttribute)) {
				timestampTokens = makeTimestampTokens(unsignedAttribute, TimestampType.SIGNATURE_TIMESTAMP, getSignatureTimestampReferences());
				if (Utils.isCollectionEmpty(timestampTokens)) {
					continue;
				}
				signatureTimestamps.addAll(timestampTokens);
				
			} else if (isCompleteCertificateRef(unsignedAttribute)) {
				addReferences(encapsulatedReferences, getTimestampedCertificateRefs(unsignedAttribute));
				continue;
				
			} else if (isAttributeCertificateRef(unsignedAttribute)) {
				addReferences(encapsulatedReferences, getTimestampedCertificateRefs(unsignedAttribute));
				continue;
				
			} else if (isCompleteRevocationRef(unsignedAttribute)) {
				addReferences(encapsulatedReferences, getTimestampedRevocationRefs(unsignedAttribute));
				continue;
				
			} else if (isAttributeRevocationRef(unsignedAttribute)) {
				addReferences(encapsulatedReferences, getTimestampedRevocationRefs(unsignedAttribute));
				continue;
				
			} else if (isRefsOnlyTimestamp(unsignedAttribute)) {
				timestampTokens = makeTimestampTokens(unsignedAttribute, TimestampType.VALIDATION_DATA_REFSONLY_TIMESTAMP, encapsulatedReferences);
				if (Utils.isCollectionEmpty(timestampTokens)) {
					continue;
				}
				refsOnlyTimestamps.addAll(timestampTokens);
				
			} else if (isSigAndRefsTimestamp(unsignedAttribute)) {
				final List<TimestampedReference> references = new ArrayList<>();
				addReferencesFromPreviousTimestamps(references, filterSignatureTimestamps(timestamps));
				addReferences(references, encapsulatedReferences);
				
				timestampTokens = makeTimestampTokens(unsignedAttribute, TimestampType.VALIDATION_DATA_TIMESTAMP, references);
				if (Utils.isCollectionEmpty(timestampTokens)) {
					continue;
				}
				sigAndRefsTimestamps.addAll(timestampTokens);
				
			} else if (isCertificateValues(unsignedAttribute)) {
				addReferences(encapsulatedReferences, getTimestampedCertificateValues(unsignedAttribute));
				continue;
				
			} else if (isRevocationValues(unsignedAttribute)) {
				addReferences(encapsulatedReferences, getTimestampedRevocationValues(unsignedAttribute));
				continue;
				
			} else if (isAttrAuthoritiesCertValues(unsignedAttribute)) {
				addReferences(encapsulatedReferences, getTimestampedCertificateValues(unsignedAttribute));
				continue;
				
			} else if (isAttributeRevocationValues(unsignedAttribute)) {
				addReferences(encapsulatedReferences, getTimestampedRevocationValues(unsignedAttribute));
				continue;
				
			} else if (isArchiveTimestamp(unsignedAttribute)) {
				final List<TimestampedReference> references = new ArrayList<>();
				addReferencesFromPreviousTimestamps(references, timestamps);
				addReferences(references, getAllSignedDataReferences());
				addReferences(references, encapsulatedReferences);
				
				timestampTokens = makeTimestampTokens(unsignedAttribute, TimestampType.ARCHIVE_TIMESTAMP, references);
				if (Utils.isCollectionEmpty(timestampTokens)) {
					continue;
				}
				setArchiveTimestampType(timestampTokens, unsignedAttribute);
				incorporateArchiveTimestampOtherReferences(timestampTokens);
				
				// reset the list, because a new 'arcTst' has been found
				previousArcTstReferences = new ArrayList<>();

				addPreviousArcTSTsReferences(previousArcTstReferences, timestampTokens);
				archiveTimestamps.addAll(timestampTokens);
				
			} else if (isPreviousDataArchiveTimestamp(unsignedAttribute)) {
				final List<TimestampedReference> references = new ArrayList<>();
				addReferences(references, previousArcTstReferences);
				
				timestampTokens = makeTimestampTokens(unsignedAttribute, TimestampType.ARCHIVE_TIMESTAMP, references);
				if (Utils.isCollectionEmpty(timestampTokens)) {
					continue;
				}
				setArchiveTimestampType(timestampTokens, unsignedAttribute);
				
				// reset the list, because a new 'arcTst' has been found
				previousArcTstReferences = new ArrayList<>();
				
				addPreviousArcTSTsReferences(previousArcTstReferences, timestampTokens);
				archiveTimestamps.addAll(timestampTokens);
				
			} else if (isTimeStampValidationData(unsignedAttribute)) {
				List<TimestampedReference> timestampValidationData = getTimestampValidationData(unsignedAttribute);
				addReferences(encapsulatedReferences, timestampValidationData);
				// required for Archive TSTs of PREVIOUS_ARC_TST type
				addReferences(previousArcTstReferences, timestampValidationData);
				continue;
				
			} else if (isCounterSignature(unsignedAttribute)) {
				List<TimestampedReference> counterSignatureReferences = getCounterSignatureReferences(unsignedAttribute);
				addReferences(encapsulatedReferences, counterSignatureReferences);
				continue;

			} else {
				LOG.warn("The unsigned attribute with a name [{}] is not supported", unsignedAttribute);
				continue;
			}
			
			populateSources(timestampTokens);
			timestamps.addAll(timestampTokens);
			
		}
		
	}

	/**
	 * Returns the 'signed-signature-properties' element of the signature
	 * @return {@link SignatureProperties}
	 */
	protected abstract SignatureProperties<SignatureAttribute> getSignedSignatureProperties();
	
	/**
	 * Returns the 'unsigned-signature-properties' element of the signature
	 * @return {@link SignatureProperties}
	 */
	protected abstract SignatureProperties<SignatureAttribute> getUnsignedSignatureProperties();

	/**
	 * Determines if the given {@code signedAttribute} is an instance of "content-timestamp" element
	 * NOTE: Applicable only for CAdES
	 * @param signedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a Data Objects Timestamp, FALSE otherwise
	 */
	protected abstract boolean isContentTimestamp(SignatureAttribute signedAttribute);
	
	/**
	 * Determines if the given {@code signedAttribute} is an instance of "data-objects-timestamp" element
	 * NOTE: Applicable only for XAdES
	 * @param signedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a Data Objects Timestamp, FALSE otherwise
	 */
	protected abstract boolean isAllDataObjectsTimestamp(SignatureAttribute signedAttribute);
	
	/**
	 * Determines if the given {@code signedAttribute} is an instance of "individual-data-objects-timestamp" element
	 * NOTE: Applicable only for XAdES
	 * @param signedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a Data Objects Timestamp, FALSE otherwise
	 */
	protected abstract boolean isIndividualDataObjectsTimestamp(SignatureAttribute signedAttribute);
	
	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "signature-timestamp" element
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a Signature Timestamp, FALSE otherwise
	 */
	protected abstract boolean isSignatureTimestamp(SignatureAttribute unsignedAttribute);
	
	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "complete-certificate-ref" element
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a Complete Certificate Ref, FALSE otherwise
	 */
	protected abstract boolean isCompleteCertificateRef(SignatureAttribute unsignedAttribute);
	
	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "attribute-certificate-ref" element
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is an Attribute Certificate Ref, FALSE otherwise
	 */
	protected abstract boolean isAttributeCertificateRef(SignatureAttribute unsignedAttribute);
	
	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "complete-revocation-ref" element
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a Complete Revocation Ref, FALSE otherwise
	 */
	protected abstract boolean isCompleteRevocationRef(SignatureAttribute unsignedAttribute);
	
	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "attribute-revocation-ref" element
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is an Attribute Revocation Ref, FALSE otherwise
	 */
	protected abstract boolean isAttributeRevocationRef(SignatureAttribute unsignedAttribute);
	
	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "refs-only-timestamp" element
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a Refs Only TimeStamp, FALSE otherwise
	 */
	protected abstract boolean isRefsOnlyTimestamp(SignatureAttribute unsignedAttribute);
	
	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "sig-and-refs-timestamp" element
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a Sig And Refs TimeStamp, FALSE otherwise
	 */
	protected abstract boolean isSigAndRefsTimestamp(SignatureAttribute unsignedAttribute);
	
	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "certificate-values" element
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a Certificate Values, FALSE otherwise
	 */
	protected abstract boolean isCertificateValues(SignatureAttribute unsignedAttribute);
	
	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "revocation-values" element
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a Revocation Values, FALSE otherwise
	 */
	protected abstract boolean isRevocationValues(SignatureAttribute unsignedAttribute);

	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "AttrAuthoritiesCertValues" element
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is an AttrAuthoritiesCertValues, FALSE otherwise
	 */
	protected abstract boolean isAttrAuthoritiesCertValues(SignatureAttribute unsignedAttribute);

	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "AttributeRevocationValues" element
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is an AttributeRevocationValues, FALSE otherwise
	 */
	protected abstract boolean isAttributeRevocationValues(SignatureAttribute unsignedAttribute);
	
	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "archive-timestamp" element
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is an Archive TimeStamp, FALSE otherwise
	 */
	protected abstract boolean isArchiveTimestamp(SignatureAttribute unsignedAttribute);

	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "archive-timestamp" element
	 * with "previousArcTst" type
	 * NOTE: used in JAdES
	 * 
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a Previous Data Archive TimeStamp, FALSE otherwise
	 */
	protected abstract boolean isPreviousDataArchiveTimestamp(SignatureAttribute unsignedAttribute);
	
	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of
	 * "timestamp-validation-data" element
	 * 
	 * @param unsignedAttribute {@link SignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a TimeStamp Validation Data,
	 *         FALSE otherwise
	 */
	protected abstract boolean isTimeStampValidationData(SignatureAttribute unsignedAttribute);

	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of
	 * "counter-signature" element
	 * 
	 * @param unsignedAttribute {@link SignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a Counter signature, FALSE
	 *         otherwise
	 */
	protected abstract boolean isCounterSignature(SignatureAttribute unsignedAttribute);

	/**
	 * Creates a timestamp token from the provided {@code signatureAttribute}
	 * @param signatureAttribute {@link ISignatureAttribute} to create timestamp from
	 * @param timestampType a target {@link TimestampType}
	 * @param references list of {@link TimestampedReference}s covered by the current timestamp
	 * @return {@link TimestampToken}
	 */
	protected abstract TimestampToken makeTimestampToken(SignatureAttribute signatureAttribute, TimestampType timestampType,
			List<TimestampedReference> references);
	
	/**
	 * Creates timestamp tokens from the provided {@code signatureAttribute}
	 * @param signatureAttribute {@link ISignatureAttribute} to create timestamp from
	 * @param timestampType a target {@link TimestampType}
	 * @param references list of {@link TimestampedReference}s covered by the current timestamp
	 * @return a list of {@link TimestampToken}s
	 */
	protected List<TimestampToken> makeTimestampTokens(SignatureAttribute signatureAttribute, TimestampType timestampType,
			List<TimestampedReference> references) {
		TimestampToken timestampToken = makeTimestampToken(signatureAttribute, timestampType, references);
		if (timestampToken != null) {
			return Collections.singletonList(timestampToken);
		}
		return Collections.emptyList();
	}
	
	@Override
	public List<TimestampedReference> getAllSignedDataReferences() {
		final List<TimestampedReference> references = new ArrayList<>();
		
		List<SignatureScope> signatureScopes = signature.getSignatureScopes();
		if (Utils.isCollectionNotEmpty(signatureScopes)) {
			for (SignatureScope signatureScope : signatureScopes) {
				addReference(references, new TimestampedReference(signatureScope.getDSSIdAsString(), TimestampedObjectType.SIGNED_DATA));
			}
		}
		return references;
	}
	
	/**
	 * Returns a list of {@link TimestampedReference}s for an "individual-data-objects-timestamp"
	 * NOTE: Used only in XAdES
	 * @param signedAttribute {@link SignatureAttribute}
	 * @return a list of {@link TimestampedReference}s
	 */
	protected abstract List<TimestampedReference> getIndividualContentTimestampedReferences(SignatureAttribute signedAttribute);
	
	/**
	 * Returns a list of {@link TimestampedReference} for a "signature-timestamp" element
	 * @return list of {@link TimestampedReference}s
	 */
	public List<TimestampedReference> getSignatureTimestampReferences() {
		final List<TimestampedReference> references = new ArrayList<>();
		addReferencesFromPreviousTimestamps(references, getContentTimestamps());
		addReferences(references, getAllSignedDataReferences());
		addReference(references, new TimestampedReference(signature.getId(), TimestampedObjectType.SIGNATURE));
		addReferences(references, getSigningCertificateTimestampReferences());
		return references;
	}

	/**
	 * Returns a list of {@code TimestampedReference}s created from signing certificates of the signature
	 * @return list of {@link TimestampedReference}s
	 */
	protected List<TimestampedReference> getSigningCertificateTimestampReferences() {
		SignatureCertificateSource signatureCertificateSource = signature.getCertificateSource();
		return createReferencesForCertificates(signatureCertificateSource.getSigningCertificates());
	}
	
	/**
	 * Creates a list of {@code TimestampedReference}s for the provided list of {@code certificates}
	 * @param certificates collection of {@link CertificateToken}s
	 * @return list of {@link TimestampedReference}s
	 */
	protected List<TimestampedReference> createReferencesForCertificates(Collection<CertificateToken> certificates) {
		final List<TimestampedReference> references = new ArrayList<>();
		for (CertificateToken certificateToken : certificates) {
			addReference(references, new TimestampedReference(certificateToken.getDSSIdAsString(), TimestampedObjectType.CERTIFICATE));
		}
		return references;
	}
	
	/**
	 * Returns a list of {@link TimestampedReference} certificate refs found in the
	 * given {@code unsignedAttribute}
	 * 
	 * @param unsignedAttribute {@link SignatureAttribute} to find references from
	 * @return list of {@link TimestampedReference}s
	 */
	protected List<TimestampedReference> getTimestampedCertificateRefs(SignatureAttribute unsignedAttribute) {
		return getTimestampedCertificateRefs(getCertificateRefs(unsignedAttribute), certificateSource);
	}
	
	/**
	 * Returns a list of timestamped references from the given collection of {@code certificateRefs}
	 * 
	 * @param certificateRefs a collection of {@link CertificateRef}s to get timestamped references from
	 * @param listCertificateSource {@link ListCertificateSource} to find certificate binaries from if present
	 * @return a list of {@link TimestampedReference}s
	 */
	protected List<TimestampedReference> getTimestampedCertificateRefs(Collection<CertificateRef> certificateRefs, ListCertificateSource listCertificateSource) {
		List<TimestampedReference> timestampedReferences = new ArrayList<>();
		for (CertificateRef certRef : certificateRefs) {
			Set<CertificateToken> certificateTokens = listCertificateSource.findTokensFromRefs(certRef);
			if (Utils.isCollectionNotEmpty(certificateTokens)) {
				for (CertificateToken token : certificateTokens) {
					timestampedReferences.add(new TimestampedReference(token.getDSSIdAsString(), TimestampedObjectType.CERTIFICATE));
				}
			} else {
				timestampedReferences.add(new TimestampedReference(certRef.getDSSIdAsString(), TimestampedObjectType.CERTIFICATE));
			}
		}
		return timestampedReferences;
	}
	
	/**
	 * Returns a list of {@link CertificateRef}s from the given
	 * {@code unsignedAttribute}
	 * 
	 * @param unsignedAttribute {@link SignatureAttribute} to get certRefs from
	 * @return list of {@link CertificateRef}s
	 */
	protected abstract List<CertificateRef> getCertificateRefs(SignatureAttribute unsignedAttribute);
	
	/**
	 * Returns a list of {@link TimestampedReference} revocation refs found in the given {@code unsignedAttribute}
	 * @param unsignedAttribute {@link SignatureAttribute} to find references from
	 * @return list of {@link TimestampedReference}s
	 */
	protected List<TimestampedReference> getTimestampedRevocationRefs(SignatureAttribute unsignedAttribute) {
		List<TimestampedReference> timestampedReferences = new ArrayList<>();
		timestampedReferences.addAll(getTimestampedCRLRefs(getCRLRefs(unsignedAttribute), crlSource));
		timestampedReferences.addAll(getTimestampedOCSPRefs(getOCSPRefs(unsignedAttribute), ocspSource));
		return timestampedReferences;
	}

	/**
	 * Returns a list of timestamped references from the given collection of {@code crlRefs}
	 * 
	 * @param crlRefs a collection of {@link CRLRef}s to get timestamped references from
	 * @param crlRevocationSource {@link ListRevocationSource} to find CRL binaries from if present
	 * @return a list of {@link TimestampedReference}s
	 */
	protected List<TimestampedReference> getTimestampedCRLRefs(Collection<CRLRef> crlRefs, ListRevocationSource<CRL> crlRevocationSource) {
		List<TimestampedReference> timestampedReferences = new ArrayList<>();
		for (CRLRef crlRef : crlRefs) {
			EncapsulatedRevocationTokenIdentifier<CRL> token = crlRevocationSource.findBinaryForReference(crlRef);
			if (token != null) {
				timestampedReferences.add(new TimestampedReference(token.asXmlId(), TimestampedObjectType.REVOCATION));
			} else {
				timestampedReferences.add(new TimestampedReference(crlRef.getDSSIdAsString(), TimestampedObjectType.REVOCATION));
			}
		}
		return timestampedReferences;
	}

	/**
	 * Returns a list of timestamped references from the given collection of {@code ocspRefs}
	 * 
	 * @param ocspRefs a collection of {@link OCSPRef}s to get timestamped references from
	 * @param ocspRevocationSource {@link ListRevocationSource} to find OCSP binaries from if present
	 * @return a list of {@link TimestampedReference}s
	 */
	protected List<TimestampedReference> getTimestampedOCSPRefs(Collection<OCSPRef> ocspRefs, ListRevocationSource<OCSP> ocspRevocationSource) {
		List<TimestampedReference> timestampedReferences = new ArrayList<>();
		for (OCSPRef ocspRef : ocspRefs) {
			EncapsulatedRevocationTokenIdentifier<OCSP> token = ocspRevocationSource.findBinaryForReference(ocspRef);
			if (token != null) {
				timestampedReferences.add(new TimestampedReference(token.asXmlId(), TimestampedObjectType.REVOCATION));
			} else {
				timestampedReferences.add(new TimestampedReference(ocspRef.getDSSIdAsString(), TimestampedObjectType.REVOCATION));
			}
		}
		return timestampedReferences;
	}
	
	/**
	 * Returns a list of CRL revocation refs from the given
	 * {@code unsignedAttribute}
	 * 
	 * @param unsignedAttribute {@link SignatureAttribute} to get CRLRef
	 * 
	 * @return list of {@link CRLRef}s
	 */
	protected abstract List<CRLRef> getCRLRefs(SignatureAttribute unsignedAttribute);
	
	/**
	 * Returns a list of OCSP revocation refs from the given
	 * {@code unsignedAttribute}
	 * 
	 * @param unsignedAttribute {@link SignatureAttribute} to get OCSPRefs from
	 * @return list of {@link OCSPRef}s
	 */
	protected abstract List<OCSPRef> getOCSPRefs(SignatureAttribute unsignedAttribute);
	
	/**
	 * Returns a list of {@code TimestampedReference}s from the {@code unsignedAttribute} containing certificate values
	 * 
	 * @param unsignedAttribute {@link SignatureAttribute} to extract certificate values from
	 * @return a list of {@link TimestampedReference}s
	 */
	protected List<TimestampedReference> getTimestampedCertificateValues(SignatureAttribute unsignedAttribute) {
		List<TimestampedReference> timestampedReferences = new ArrayList<>();
		for (Identifier certificateIdentifier : getEncapsulatedCertificateIdentifiers(unsignedAttribute)) {
			timestampedReferences.add(new TimestampedReference(certificateIdentifier.asXmlId(), TimestampedObjectType.CERTIFICATE));
		}
		return timestampedReferences;
	}
	
	/**
	 * Returns a list of {@link Identifier}s obtained from the given {@code unsignedAttribute}
	 * @param unsignedAttribute {@link SignatureAttribute} to get certificate identifiers from
	 * @return list of {@link Identifier}s
	 */
	protected abstract List<Identifier> getEncapsulatedCertificateIdentifiers(SignatureAttribute unsignedAttribute);
	
	protected List<TimestampedReference> getTimestampedRevocationValues(SignatureAttribute unsignedAttribute) {
		List<TimestampedReference> timestampedReferences = new ArrayList<>();
		for (Identifier revocationIdentifier : getEncapsulatedCRLIdentifiers(unsignedAttribute)) {
			timestampedReferences.add(new TimestampedReference(revocationIdentifier.asXmlId(), TimestampedObjectType.REVOCATION));
		}
		for (Identifier revocationIdentifier : getEncapsulatedOCSPIdentifiers(unsignedAttribute)) {
			timestampedReferences.add(new TimestampedReference(revocationIdentifier.asXmlId(), TimestampedObjectType.REVOCATION));
		}
		return timestampedReferences;
	}
	
	/**
	 * Returns a list of {@link Identifier}s obtained from the given {@code unsignedAttribute}
	 * @param unsignedAttribute {@link SignatureAttribute} to get CRL identifiers from
	 * @return list of {@link Identifier}s
	 */
	protected abstract List<Identifier> getEncapsulatedCRLIdentifiers(SignatureAttribute unsignedAttribute);
	
	/**
	 * Returns a list of {@link Identifier}s obtained from the given {@code unsignedAttribute}
	 * @param unsignedAttribute {@link SignatureAttribute} to get OCSP identifiers from
	 * @return list of {@link Identifier}s
	 */
	protected abstract List<Identifier> getEncapsulatedOCSPIdentifiers(SignatureAttribute unsignedAttribute);
	
	private void incorporateArchiveTimestampOtherReferences(List<TimestampToken> timestampTokens) {
		for (TimestampToken timestampToken : timestampTokens) {
			addReferences(timestampToken.getTimestampedReferences(), getArchiveTimestampOtherReferences(timestampToken));
		}
	}
	
	/**
	 * Returns a list of {@code TimestampedReference}s for the given archive {@code timestampToken}
	 * that cannot be extracted from signature attributes (signed or unsigned),
	 * depending on its format (signedData for CAdES or, keyInfo for XAdES)
	 * 
	 * @param timestampToken {@link TimestampToken} to get archive timestamp references for
	 * @return list of {@link TimestampedReference}s
	 */
	protected abstract List<TimestampedReference> getArchiveTimestampOtherReferences(TimestampToken timestampToken);

	/**
	 * Returns a list of all {@code TimestampedReference}s found into CMS SignedData of the signature
	 * NOTE: used only in ASiC-E CAdES
	 * 
	 * @return list of {@link TimestampedReference}s
	 */
	protected List<TimestampedReference> getSignatureSignedDataReferences() {
		// empty by default
		return new ArrayList<>();
	}
	
	/**
	 * Returns a list of {@link TimestampedReference}s encapsulated to the "timestamp-validation-data" {@code unsignedAttribute}
	 * 
	 * @param unsignedAttribute {@link SignatureAttribute} to get timestamped references from
	 * @return list of {@link TimestampedReference}s
	 */
	protected List<TimestampedReference> getTimestampValidationData(SignatureAttribute unsignedAttribute) {
		List<TimestampedReference> timestampedReferences = new ArrayList<>();
		for (Identifier certificateIdentifier : getEncapsulatedCertificateIdentifiers(unsignedAttribute)) {
			timestampedReferences.add(new TimestampedReference(certificateIdentifier.asXmlId(), TimestampedObjectType.CERTIFICATE));
		}
		for (Identifier crlIdentifier : getEncapsulatedCRLIdentifiers(unsignedAttribute)) {
			timestampedReferences.add(new TimestampedReference(crlIdentifier.asXmlId(), TimestampedObjectType.REVOCATION));
		}
		for (Identifier ocspIdentifier : getEncapsulatedOCSPIdentifiers(unsignedAttribute)) {
			timestampedReferences.add(new TimestampedReference(ocspIdentifier.asXmlId(), TimestampedObjectType.REVOCATION));
		}
		return timestampedReferences;
	}

	/**
	 * Returns a list of {@link TimestampedReference}s encapsulated to the "timestamp-validation-data" {@code unsignedAttribute}
	 * 
	 * @param unsignedAttribute {@link SignatureAttribute} to get timestamped references from
	 * @return list of {@link TimestampedReference}s
	 */
	protected List<TimestampedReference> getCounterSignatureReferences(SignatureAttribute unsignedAttribute) {
		List<TimestampedReference> cSigReferences = new ArrayList<>();
		
		AdvancedSignature counterSignature = getCounterSignature(unsignedAttribute);
		if (counterSignature != null) {
			cSigReferences.add(new TimestampedReference(counterSignature.getId(), TimestampedObjectType.SIGNATURE));
			addReferences(cSigReferences, createReferencesForCertificates(counterSignature.getCertificateSource().getCertificates()));

			TimestampSource timestampSource = counterSignature.getTimestampSource();
			addReferences(cSigReferences, timestampSource.getAllSignedDataReferences());
			addReferences(cSigReferences, timestampSource.getEncapsulatedReferences());
			addReferencesFromPreviousTimestamps(cSigReferences, timestampSource.getAllTimestamps());
		}
		
		return cSigReferences;
	}
	
	/**
	 * Extracts a Counter Signature from the given {@code unsignedAttribute}
	 * 
	 * @param unsignedAttribute {@link SignatureAttribute} containing a counter signature
	 * @return {@link AdvancedSignature} representing a counter signature
	 */
	protected abstract AdvancedSignature getCounterSignature(SignatureAttribute unsignedAttribute);
	
	/**
	 * Adds {@code referenceToAdd} to {@code referenceList} without duplicates
	 * @param referenceList - list of {@link TimestampedReference}s to be extended
	 * @param referenceToAdd - {@link TimestampedReference} to be added
	 */
	protected void addReference(List<TimestampedReference> referenceList, TimestampedReference referenceToAdd) {
		addReferences(referenceList, Arrays.asList(referenceToAdd));
	}

	/**
	 * Adds a reference for the given identifier and category
	 * 
	 * @param referenceList - list of {@link TimestampedReference}s to be extended
	 * @param identifier    - {@link Identifier} to be added
	 * @param category      - {@link TimestampedObjectType} to be added
	 */
	protected void addReference(List<TimestampedReference> referenceList, Identifier identifier,
			TimestampedObjectType category) {
		addReferences(referenceList, Arrays.asList(new TimestampedReference(identifier.asXmlId(), category)));
	}

	/**
	 * Adds {@code referencesToAdd} to {@code referenceList} without duplicates
	 * @param referenceList - list of {@link TimestampedReference}s to be extended
	 * @param referencesToAdd - {@link TimestampedReference}s to be added
	 */
	protected void addReferences(List<TimestampedReference> referenceList, List<TimestampedReference> referencesToAdd) {
		for (TimestampedReference reference : referencesToAdd) {
			if (!referenceList.contains(reference)) {
				referenceList.add(reference);
			}
		}
	}

	private List<TimestampToken> filterSignatureTimestamps(List<TimestampToken> previousTimestampedTimestamp) {
		List<TimestampToken> result = new ArrayList<>();
		for (TimestampToken timestampToken : previousTimestampedTimestamp) {
			if (TimestampType.SIGNATURE_TIMESTAMP.equals(timestampToken.getTimeStampType())) {
				result.add(timestampToken);
			}
		}
		return result;
	}
	
	/**
	 * Incorporates all references to tokens incorporated in the timestamps, as well as a reference to the timestamps itself
	 * 
	 * @param references a list of {@link TimestampedReference}s to populate 
	 * @param timestampedTimestamps a list of {@link TimestampToken}s to extract values from
	 */
	protected void addPreviousArcTSTsReferences(List<TimestampedReference> references, List<TimestampToken> timestampedTimestamps) {
		if (Utils.isCollectionNotEmpty(timestampedTimestamps)) {
			for (final TimestampToken timestampToken : timestampedTimestamps) {
				addReference(references, new TimestampedReference(timestampToken.getDSSIdAsString(), TimestampedObjectType.TIMESTAMP));
				addEncapsulatedValuesFromTimestamp(references, timestampToken);
			}
		}
	}

	/**
	 * Incorporates all references timestamped by the previous timestamps, including references to tokens incorporated in the timestamps,
	 * as well as a reference to the timestamps itself
	 * 
	 * @param references a list of {@link TimestampedReference}s to populate 
	 * @param timestampedTimestamps a list of {@link TimestampToken}s to extract values from
	 */
	protected void addReferencesFromPreviousTimestamps(List<TimestampedReference> references, List<TimestampToken> timestampedTimestamps) {
		if (Utils.isCollectionNotEmpty(timestampedTimestamps)) {
			for (final TimestampToken timestampToken : timestampedTimestamps) {
				addReference(references, new TimestampedReference(timestampToken.getDSSIdAsString(), TimestampedObjectType.TIMESTAMP));
				addTimestampedReferences(references, timestampToken);
				addEncapsulatedValuesFromTimestamp(references, timestampToken);
			}
		}
	}
	
	private void addTimestampedReferences(List<TimestampedReference> references, TimestampToken timestampedTimestamp) {
		for (TimestampedReference timestampedReference : timestampedTimestamp.getTimestampedReferences()) {
			addReference(references, timestampedReference);
		}
	}
	
	/**
	 * Adds to the {@code references} list all validation data embedded to the {@code timestampedTimestamp}
	 * @param references list of {@link TimestampedReference}s to extend
	 * @param timestampedTimestamp {@link TimestampToken} to extract embedded values from
	 */
	protected void addEncapsulatedValuesFromTimestamp(List<TimestampedReference> references, TimestampToken timestampedTimestamp) {
		for (final CertificateToken certificate : timestampedTimestamp.getCertificates()) {
			addReference(references, certificate.getDSSId(), TimestampedObjectType.CERTIFICATE);
		}
		for (final CertificateRef certificateRef : timestampedTimestamp.getCertificateRefs()) {
			addReference(references, new TimestampedReference(certificateRef.getDSSIdAsString(), TimestampedObjectType.CERTIFICATE));
		}
		TimestampCRLSource timestampCRLSource = timestampedTimestamp.getCRLSource();
		for (EncapsulatedRevocationTokenIdentifier<CRL> revocationBinary : timestampCRLSource.getAllRevocationBinaries()) {
			addReference(references, revocationBinary, TimestampedObjectType.REVOCATION);
		}
		for (EncapsulatedRevocationTokenIdentifier<CRL> revocationBinary : timestampCRLSource.getAllReferencedRevocationBinaries()) {
			addReference(references, revocationBinary, TimestampedObjectType.REVOCATION);
		}
		TimestampOCSPSource timestampOCSPSource = timestampedTimestamp.getOCSPSource();
		for (EncapsulatedRevocationTokenIdentifier<OCSP> revocationBinary : timestampOCSPSource.getAllRevocationBinaries()) {
			addReference(references, revocationBinary, TimestampedObjectType.REVOCATION);
		}
		for (EncapsulatedRevocationTokenIdentifier<OCSP> revocationBinary : timestampOCSPSource.getAllReferencedRevocationBinaries()) {
			addReference(references, revocationBinary, TimestampedObjectType.REVOCATION);
		}
	}
	
	private void setArchiveTimestampType(List<TimestampToken> timestampTokens, SignatureAttribute unsignedAttribute) {
		ArchiveTimestampType archiveTimestampType = getArchiveTimestampType(unsignedAttribute);
		for (TimestampToken timestampToken : timestampTokens) {
			timestampToken.setArchiveTimestampType(archiveTimestampType);
		}
	}

	/**
	 * Returns {@link ArchiveTimestampType} for the given {@code unsignedAttribute}
	 * @param unsignedAttribute {@link SignatureAttribute} to get archive timestamp type for
	 */
	protected abstract ArchiveTimestampType getArchiveTimestampType(SignatureAttribute unsignedAttribute);
	
	/**
	 * Validates list of all timestamps present in the source
	 */
	protected void validateTimestamps() {
		
		TimestampDataBuilder timestampDataBuilder = getTimestampDataBuilder();

		/*
		 * This validates the content-timestamp tokensToProcess present in the signature.
		 */
		for (final TimestampToken timestampToken : getContentTimestamps()) {
			final DSSDocument timestampedData = timestampDataBuilder.getContentTimestampData(timestampToken);
			timestampToken.matchData(timestampedData);
		}

		/*
		 * This validates the signature timestamp tokensToProcess present in the signature.
		 */
		for (final TimestampToken timestampToken : getSignatureTimestamps()) {
			final DSSDocument timestampedData = timestampDataBuilder.getSignatureTimestampData(timestampToken);
			timestampToken.matchData(timestampedData);
		}

		/*
		 * This validates the SigAndRefs timestamp tokensToProcess present in the signature.
		 */
		for (final TimestampToken timestampToken : getTimestampsX1()) {
			final DSSDocument timestampedData = timestampDataBuilder.getTimestampX1Data(timestampToken);
			timestampToken.matchData(timestampedData);
		}

		/*
		 * This validates the RefsOnly timestamp tokensToProcess present in the signature.
		 */
		for (final TimestampToken timestampToken : getTimestampsX2()) {
			final DSSDocument timestampedData = timestampDataBuilder.getTimestampX2Data(timestampToken);
			timestampToken.matchData(timestampedData);
		}

		/*
		 * This validates the archive timestamp tokensToProcess present in the signature.
		 */
		for (final TimestampToken timestampToken : getArchiveTimestamps()) {
			if (!timestampToken.isProcessed()) {
				final DSSDocument timestampedData = timestampDataBuilder.getArchiveTimestampData(timestampToken);
				timestampToken.matchData(timestampedData);
			}
		}
		
	}
	
	/**
	 * Returns a related {@link TimestampDataBuilder}
	 * @return {@link TimestampDataBuilder}
	 */
	protected abstract TimestampDataBuilder getTimestampDataBuilder();

	private void processExternalTimestamp(TimestampToken externalTimestamp) {
		// add all validation data present in Signature CMS SignedData, because an external timestamp covers a whole signature file
		addReferences(externalTimestamp.getTimestampedReferences(), getSignatureSignedDataReferences());
		// add references from previously added timestamps
		addReferencesFromPreviousTimestamps(externalTimestamp.getTimestampedReferences(), getAllTimestamps());
		// populate timestamp certificate source with values present in the timestamp
		populateSources(externalTimestamp);
	}
	
	/**
	 * Allows to populate all merged sources with extracted from a timestamp data
	 * 
	 * @param timestampTokens a list of {@link TimestampToken}s to populate data from
	 */
	protected void populateSources(List<TimestampToken> timestampTokens) {
		for (TimestampToken timestampToken : timestampTokens) {
			populateSources(timestampToken);
		}
	}
	
	/**
	 * Allows to populate all merged sources with extracted from a timestamp data
	 * 
	 * @param timestampToken {@link TimestampToken} to populate data from
	 */
	protected void populateSources(TimestampToken timestampToken) {
		if (timestampToken != null) {
			certificateSource.add(timestampToken.getCertificateSource());
			crlSource.add(timestampToken.getCRLSource());
			ocspSource.add(timestampToken.getOCSPSource());
		}
	}
	
	@Override
	public boolean isTimestamped(String tokenId, TimestampedObjectType objectType) {
		return isTimestamped(signature, tokenId, objectType);
	}
	
	private boolean isTimestamped(AdvancedSignature signature, String tokenId, TimestampedObjectType objectType) {
		for (TimestampToken timestampToken : getAllTimestamps()) {
			if (timestampToken.getTimestampedReferences().contains(new TimestampedReference(tokenId, objectType))) {
				return true;
			}
		}
		AdvancedSignature masterSignature = signature.getMasterSignature();
		if (masterSignature != null) {
			return isTimestamped(masterSignature, tokenId, objectType);
		}
		
		return false;
	}

}
