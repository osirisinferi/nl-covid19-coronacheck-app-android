package nl.rijksoverheid.ctr.introduction.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/*
 *  Copyright (c) 2021 De Staat der Nederlanden, Ministerie van Volksgezondheid, Welzijn en Sport.
 *   Licensed under the EUROPEAN UNION PUBLIC LICENCE v. 1.2
 *
 *   SPDX-License-Identifier: EUPL-1.2
 *
 */
sealed class IntroductionStatus : Parcelable {
    sealed class IntroductionFinished : IntroductionStatus() {

        @Parcelize
        data class ConsentNeeded(val newTerms: NewTerms) : IntroductionStatus(), Parcelable

        @Parcelize
        object NoActionRequired : IntroductionStatus(), Parcelable

    }

    @Parcelize
    object IntroductionNotFinished : IntroductionStatus(), Parcelable
}
