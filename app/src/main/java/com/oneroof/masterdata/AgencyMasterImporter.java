package com.oneroof.masterdata;

import java.util.ArrayList;
import java.util.List;

import com.oneroof.shared.OriginalDataImporter;
import com.oneroof.shared.OriginalFile;
import com.oneroof.shared.Provenance;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the agency master. Each agency's representative, address and bank account become identity
 * rows effective from the agency's registration date, because the original records no later change.
 */
@Component
@Order(10)
class AgencyMasterImporter implements OriginalDataImporter {

	static final String FILE = "01_corevia/master/m_agency_cv.csv";

	private final AgencyRepository agencies;
	private final AgencyIdentityRepository identities;

	AgencyMasterImporter(AgencyRepository agencies, AgencyIdentityRepository identities) {
		this.agencies = agencies;
		this.identities = identities;
	}

	@Override
	@Transactional
	public List<ImportResult> importIfEmpty() {
		OriginalFile file = OriginalFile.open(FILE);
		if (this.agencies.count() > 0) {
			return List.of(new ImportResult(FILE, file.sha256(), 0, true));
		}
		List<Agency> newAgencies = new ArrayList<>();
		List<AgencyIdentity> newIdentities = new ArrayList<>();
		for (OriginalFile.Row row : file.rows()) {
			Agency agency = new Agency(row.text("agency_id"), row.text("agency_name"), row.text("business_reg_no"),
					row.flag("rebate_eligible"), row.date("registered_date"), row.sourceRef());
			newAgencies.add(agency);
			newIdentities.add(identity(agency, IdentityKind.REPRESENTATIVE, row.text("representative_name"), row));
			newIdentities.add(identity(agency, IdentityKind.ADDRESS, row.text("address"), row));
			newIdentities.add(identity(agency, IdentityKind.BANK_ACCOUNT, row.text("bank_account_no"), row));
		}
		this.agencies.saveAll(newAgencies);
		this.identities.saveAll(newIdentities);
		return List.of(new ImportResult(FILE, file.sha256(), newAgencies.size(), false));
	}

	private static AgencyIdentity identity(Agency agency, IdentityKind kind, String value, OriginalFile.Row row) {
		return new AgencyIdentity(agency.getAgencyId(), kind, value, agency.getRegisteredDate(), Provenance.ORIGINAL,
				row.sourceRef());
	}
}
