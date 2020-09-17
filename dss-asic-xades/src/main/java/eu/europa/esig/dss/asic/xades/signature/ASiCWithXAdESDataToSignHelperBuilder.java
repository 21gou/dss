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
package eu.europa.esig.dss.asic.xades.signature;

import java.util.Arrays;
import java.util.List;

import eu.europa.esig.dss.asic.common.ASiCExtractResult;
import eu.europa.esig.dss.asic.common.ASiCUtils;
import eu.europa.esig.dss.asic.xades.ASiCWithXAdESContainerExtractor;
import eu.europa.esig.dss.asic.xades.ASiCWithXAdESSignatureParameters;
import eu.europa.esig.dss.asic.xades.signature.asice.DataToSignASiCEWithXAdESFromArchive;
import eu.europa.esig.dss.asic.xades.signature.asice.DataToSignASiCEWithXAdESFromFiles;
import eu.europa.esig.dss.asic.xades.signature.asice.DataToSignOpenDocument;
import eu.europa.esig.dss.asic.xades.signature.asics.DataToSignASiCSWithXAdESFromArchive;
import eu.europa.esig.dss.asic.xades.signature.asics.DataToSignASiCSWithXAdESFromFiles;
import eu.europa.esig.dss.enumerations.ASiCContainerType;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.utils.Utils;

public class ASiCWithXAdESDataToSignHelperBuilder {

	private ASiCWithXAdESDataToSignHelperBuilder() {
	}

	/**
	 * Gets a {@code GetDataToSignASiCWithXAdESHelper} from the given list of documents and defined parameters
	 * 
	 * @param documents a list of {@link DSSDocument}s to get a helper from
	 * @param parameters {@link ASiCWithXAdESSignatureParameters}
	 * @return {@link GetDataToSignASiCWithXAdESHelper}
	 */
	public static GetDataToSignASiCWithXAdESHelper getGetDataToSignHelper(List<DSSDocument> documents, ASiCWithXAdESSignatureParameters parameters) {
		if (Utils.isCollectionNotEmpty(documents) && documents.size() == 1) {
			DSSDocument archiveDocument = documents.get(0);
			if (ASiCUtils.isZip(archiveDocument)) {
				return fromZipArchive(archiveDocument, parameters);
			}
		}
		return fromFiles(documents, parameters);
	}
	
	private static GetDataToSignASiCWithXAdESHelper fromZipArchive(DSSDocument archiveDocument, ASiCWithXAdESSignatureParameters parameters) {

		boolean asice = ASiCUtils.isASiCE(parameters.aSiC());
		
		ASiCWithXAdESContainerExtractor extractor = new ASiCWithXAdESContainerExtractor(archiveDocument);
		ASiCExtractResult extract = extractor.extract();
		
		if (ASiCUtils.isOpenDocument(extract.getMimeTypeDocument())) {
			return new DataToSignOpenDocument(extract.getSignedDocuments(), extract.getSignatureDocuments(), 
					extract.getManifestDocuments(), extract.getMimeTypeDocument(), extract.getRootContainer());
			
		} else if (ASiCUtils.isAsic(archiveDocument)) {
			if (!ASiCUtils.isArchiveContainsCorrectSignatureFileWithExtension(archiveDocument, ".xml")) {
				throw new UnsupportedOperationException("Container type doesn't match");
			}

			ASiCContainerType currentContainerType = ASiCUtils.getContainerType(archiveDocument, extract.getMimeTypeDocument(), 
					extract.getZipComment(), extract.getSignedDocuments());

			if (asice && ASiCContainerType.ASiC_E.equals(currentContainerType)) {
				return new DataToSignASiCEWithXAdESFromArchive(extract.getSignedDocuments(),
						extract.getSignatureDocuments(), extract.getManifestDocuments(), parameters.aSiC());
			} else if (!asice && ASiCContainerType.ASiC_S.equals(currentContainerType)) {
				return new DataToSignASiCSWithXAdESFromArchive(extract.getSignatureDocuments(),
						extract.getSignedDocuments(), parameters.aSiC());
			} else {
				throw new UnsupportedOperationException(
						String.format("Original container type '%s' vs parameter : '%s'", currentContainerType, parameters.aSiC().getContainerType()));
			}

		} else {
			return fromFiles(Arrays.asList(archiveDocument), parameters);
		}
	}

	private static GetDataToSignASiCWithXAdESHelper fromFiles(List<DSSDocument> documents, ASiCWithXAdESSignatureParameters parameters) {
		if (ASiCUtils.isASiCE(parameters.aSiC())) {
			return new DataToSignASiCEWithXAdESFromFiles(documents, parameters.aSiC());
		} else {
			return new DataToSignASiCSWithXAdESFromFiles(documents, parameters.bLevel().getSigningDate(), parameters.aSiC());
		}
	}

}
