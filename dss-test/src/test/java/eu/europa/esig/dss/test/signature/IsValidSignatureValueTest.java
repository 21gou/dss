package eu.europa.esig.dss.test.signature;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.SerializableSignatureParameters;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.signature.AbstractSignatureService;
import eu.europa.esig.dss.test.PKIFactoryAccess;
import eu.europa.esig.dss.validation.CertificateVerifier;
import eu.europa.esig.dss.validation.timestamp.TimestampToken;

public class IsValidSignatureValueTest extends PKIFactoryAccess {

	private MockService service = new MockService(getEmptyCertificateVerifier());
	private String signingAlias = null;

	@Test
	public void isValidSignatureValue() {
		ToBeSigned correct = new ToBeSigned("Hello".getBytes());
		ToBeSigned wrong = new ToBeSigned("Bye".getBytes());
		ToBeSigned empty = new ToBeSigned(new byte[] {});
		
		signingAlias = GOOD_USER;

		SignatureValue signatureValue = getToken().sign(correct, DigestAlgorithm.SHA256, getPrivateKeyEntry());
		assertTrue(service.isValidSignatureValue(correct, signatureValue, getSigningCert()));
		assertFalse(service.isValidSignatureValue(wrong, signatureValue, getSigningCert()));
		assertFalse(service.isValidSignatureValue(empty, signatureValue, getSigningCert()));

		CertificateToken currentSignCert = getSigningCert();
		assertThrows(NullPointerException.class, () -> service.isValidSignatureValue(null, signatureValue, currentSignCert));
		assertThrows(NullPointerException.class, () -> service.isValidSignatureValue(new ToBeSigned(), signatureValue, currentSignCert));
		assertThrows(NullPointerException.class, () -> service.isValidSignatureValue(correct, null, currentSignCert));
		assertThrows(NullPointerException.class, () -> service.isValidSignatureValue(correct, new SignatureValue(), currentSignCert));
		assertThrows(NullPointerException.class, () -> service.isValidSignatureValue(correct, signatureValue, null));

		SignatureAlgorithm originalAlgorithm = signatureValue.getAlgorithm();

		SignatureValue wrongSignatureValue = new SignatureValue(originalAlgorithm, "Hello".getBytes());
		SignatureValue emptySignatureValue = new SignatureValue(originalAlgorithm, new byte[] {});
		assertFalse(service.isValidSignatureValue(correct, wrongSignatureValue, getSigningCert()));
		assertFalse(service.isValidSignatureValue(correct, emptySignatureValue, getSigningCert()));

		signingAlias = EE_GOOD_USER;
		assertFalse(service.isValidSignatureValue(correct, signatureValue, getSigningCert()));

		signingAlias = GOOD_USER;

		signatureValue.setAlgorithm(SignatureAlgorithm.ECDSA_SHA256);
		assertFalse(service.isValidSignatureValue(correct, signatureValue, getSigningCert()));
		signatureValue.setAlgorithm(SignatureAlgorithm.DSA_SHA256);
		assertFalse(service.isValidSignatureValue(correct, signatureValue, getSigningCert()));
		signatureValue.setAlgorithm(SignatureAlgorithm.ED25519);
		assertFalse(service.isValidSignatureValue(correct, signatureValue, getSigningCert()));
	}

	@Override
	protected String getSigningAlias() {
		return signingAlias;
	}

	private static class MockService extends AbstractSignatureService {

		private static final long serialVersionUID = 1L;

		protected MockService(CertificateVerifier certificateVerifier) {
			super(certificateVerifier);
		}

		@Override
		public ToBeSigned getDataToSign(DSSDocument toSignDocument, SerializableSignatureParameters parameters) {
			return null;
		}

		@Override
		public DSSDocument signDocument(DSSDocument toSignDocument, SerializableSignatureParameters parameters, SignatureValue signatureValue) {
			return null;
		}

		@Override
		public DSSDocument extendDocument(DSSDocument toExtendDocument, SerializableSignatureParameters parameters) {
			return null;
		}

		@Override
		public TimestampToken getContentTimestamp(DSSDocument toSignDocument, SerializableSignatureParameters parameters) {
			return null;
		}

	}
}
