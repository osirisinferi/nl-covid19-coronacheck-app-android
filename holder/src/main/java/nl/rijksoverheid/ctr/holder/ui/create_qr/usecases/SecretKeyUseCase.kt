package nl.rijksoverheid.ctr.holder.ui.create_qr.usecases

import clmobile.Clmobile
import nl.rijksoverheid.ctr.holder.persistence.PersistenceManager
import nl.rijksoverheid.ctr.shared.ext.successString

/*
 *  Copyright (c) 2021 De Staat der Nederlanden, Ministerie van Volksgezondheid, Welzijn en Sport.
 *   Licensed under the EUROPEAN UNION PUBLIC LICENCE v. 1.2
 *
 *   SPDX-License-Identifier: EUPL-1.2
 *
 */

interface SecretKeyUseCase {
    fun json(): String
    fun persist()
}

class SecretKeyUseCaseImpl(
    private val persistenceManager: PersistenceManager
) : SecretKeyUseCase {

    override fun json(): String {
        return persistenceManager.getSecretKeyJson()
            ?: throw Exception("Secret key is not yet generated, persist first")
    }

    override fun persist() {
        if (persistenceManager.getSecretKeyJson() == null) {
            persistenceManager.saveSecretKeyJson(json = Clmobile.generateHolderSk().successString())
        }
    }
}
