package eu.europa.esig.dss.jades.validation;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.europa.esig.dss.jades.DSSJsonUtils;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.x509.CertificateSource;
import eu.europa.esig.dss.spi.x509.CommonCertificateSource;

public class KidCertificateSource extends CommonCertificateSource implements CertificateSource {

	private static final long serialVersionUID = 8202022366337914356L;

	private static final Logger LOG = LoggerFactory.getLogger(KidCertificateSource.class);

	private Map<String, CertificateToken> mapByKid = new HashMap<>();

	@Override
	public CertificateToken addCertificate(CertificateToken certificateToAdd) {
		LOG.debug("kid is not provided (generate kid following the JAdES specification)");
		return addCertificate(DSSJsonUtils.generateKid(certificateToAdd), certificateToAdd);
	}

	/**
	 * Adds a certificate for a given 'kid' (JWS/JAdES)
	 * 
	 * @param kid         the used kid in the JWS/JAdES
	 * @param certificate the related certificate token
	 * @return the certificate
	 */
	public CertificateToken addCertificate(String kid, CertificateToken certificate) {
		CertificateToken addedCertificate = super.addCertificate(certificate);
		if (mapByKid.containsKey(kid)) {
			LOG.warn("kid {} is already known, the certificate will be replaced", kid);
		}
		mapByKid.put(kid, addedCertificate);
		return addedCertificate;
	}

	public CertificateToken getCertificateByKid(String kid) {
		return mapByKid.get(kid);
	}

	@Override
	protected void reset() {
		super.reset();
		mapByKid = new HashMap<>();
	}

}
