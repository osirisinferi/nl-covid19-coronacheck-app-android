package nl.rijksoverheid.ctr.holder.ui.create_qr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.navigation.fragment.navArgs
import nl.rijksoverheid.ctr.design.ExpandedBottomSheetDialogFragment
import nl.rijksoverheid.ctr.holder.databinding.DialogYourNegativeTestResultExplanationBinding
import nl.rijksoverheid.ctr.shared.utils.PersonalDetailsUtil
import org.koin.android.ext.android.inject

/*
 *  Copyright (c) 2021 De Staat der Nederlanden, Ministerie van Volksgezondheid, Welzijn en Sport.
 *   Licensed under the EUROPEAN UNION PUBLIC LICENCE v. 1.2
 *
 *   SPDX-License-Identifier: EUPL-1.2
 *
 */
class YourNegativeTestResultExplanationDialogFragment : ExpandedBottomSheetDialogFragment() {

    private val args: YourNegativeTestResultExplanationDialogFragmentArgs by navArgs()
    private val personalDetailsUtil: PersonalDetailsUtil by inject()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        return DialogYourNegativeTestResultExplanationBinding.inflate(inflater).root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = DialogYourNegativeTestResultExplanationBinding.bind(view)

        val holder = args.holder

        val personalDetails = personalDetailsUtil.getPersonalDetails(
            firstNameInitial = holder.firstNameInitial,
            lastNameInitial = holder.lastNameInitial,
            birthDay = holder.birthDay,
            birthMonth = holder.birthMonth,
            includeBirthMonthNumber = false
        )

        binding.firstNameInitial.itemText.text = personalDetails.firstNameInitial
        binding.firstNameInitial.itemPosition.text = "1"

        binding.lastNameInitial.itemText.text = personalDetails.lastNameInitial
        binding.lastNameInitial.itemPosition.text = "2"

        binding.birthDay.itemText.text = personalDetails.birthDay
        binding.birthDay.itemPosition.text = "3"

        binding.birthMonth.itemText.text = personalDetails.birthMonth
        binding.birthMonth.itemPosition.text = "4"

        binding.close.setOnClickListener {
            dismiss()
        }

        ViewCompat.setAccessibilityDelegate(binding.close, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View?,
                info: AccessibilityNodeInfoCompat?
            ) {
                info?.setTraversalBefore(binding.title)
                super.onInitializeAccessibilityNodeInfo(host, info)
            }
        })
    }
}

