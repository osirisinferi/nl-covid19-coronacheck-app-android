package nl.rijksoverheid.ctr.holder.ui.create_qr.usecases

import nl.rijksoverheid.ctr.appconfig.CachedAppConfigUseCase
import nl.rijksoverheid.ctr.holder.ui.create_qr.models.RemoteTestResult
import nl.rijksoverheid.ctr.holder.ui.create_qr.models.ResponseError
import nl.rijksoverheid.ctr.holder.ui.create_qr.models.SignedResponseWithModel
import nl.rijksoverheid.ctr.holder.ui.create_qr.models.TestIsmResult
import nl.rijksoverheid.ctr.holder.ui.create_qr.repositories.CoronaCheckRepository
import nl.rijksoverheid.ctr.holder.ui.create_qr.repositories.TestProviderRepository
import nl.rijksoverheid.ctr.holder.ui.myoverview.usecases.TestResultAttributesUseCase
import nl.rijksoverheid.ctr.holder.ui.myoverview.utils.TokenValidatorUtil
import nl.rijksoverheid.ctr.shared.ext.removeWhitespace
import nl.rijksoverheid.ctr.shared.models.PersonalDetails
import nl.rijksoverheid.ctr.shared.utils.PersonalDetailsUtil
import nl.rijksoverheid.ctr.shared.utils.TestResultUtil
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

/*
 *  Copyright (c) 2021 De Staat der Nederlanden, Ministerie van Volksgezondheid, Welzijn en Sport.
 *   Licensed under the EUROPEAN UNION PUBLIC LICENCE v. 1.2
 *
 *   SPDX-License-Identifier: EUPL-1.2
 *
 */
class TestResultUseCase(
    private val testProviderUseCase: TestProviderUseCase,
    private val testProviderRepository: TestProviderRepository,
    private val coronaCheckRepository: CoronaCheckRepository,
    private val commitmentMessageUseCase: CommitmentMessageUseCase,
    private val secretKeyUseCase: SecretKeyUseCase,
    private val createCredentialUseCase: CreateCredentialUseCase,
    private val personalDetailsUtil: PersonalDetailsUtil,
    private val testResultUtil: TestResultUtil,
    private val cachedAppConfigUseCase: CachedAppConfigUseCase,
    private val testResultAttributesUseCase: TestResultAttributesUseCase,
    private val tokenValidatorUtil: TokenValidatorUtil
) {

    suspend fun testResult(uniqueCode: String, verificationCode: String? = null): TestResult {
        if (uniqueCode.indexOf("-") == -1) {
            return TestResult.InvalidToken
        }

        val uniqueCodeAttributes = uniqueCode.split("-")

        if (uniqueCodeAttributes.size != 3) {
            return TestResult.InvalidToken
        }

        val providerIdentifier = uniqueCodeAttributes[0]
        val token = uniqueCodeAttributes[1]
        val checksum = uniqueCodeAttributes[2]

//        if (!tokenValidatorUtil.validate(
//                token = token,
//                checksum = checksum
//            )
//        ) {
//            return TestResult.InvalidToken
//        }

        return try {
            val testProvider = testProviderUseCase.testProvider(providerIdentifier)
                ?: return TestResult.InvalidToken

            val signedResponseWithTestResult = testProviderRepository.remoteTestResult(
                url = testProvider.resultUrl,
                token = token.removeWhitespace(),
                verifierCode = verificationCode?.removeWhitespace(),
                signingCertificateBytes = testProvider.publicKey
            )

            val remoteTestResult = signedResponseWithTestResult.model

            when (remoteTestResult.status) {
                RemoteTestResult.Status.VERIFICATION_REQUIRED -> return TestResult.VerificationRequired
                RemoteTestResult.Status.INVALID_TOKEN -> return TestResult.InvalidToken
                RemoteTestResult.Status.PENDING -> return TestResult.Pending
                RemoteTestResult.Status.COMPLETE -> {
                    // nothing
                }
                else -> throw IllegalStateException("Unsupported status ${remoteTestResult.status}")
            }

            val result = remoteTestResult.result ?: error("Expected result")
            val personalDetails = personalDetailsUtil.getPersonalDetails(
                firstNameInitial = result.holder.firstNameInitial,
                lastNameInitial = result.holder.lastNameInitial,
                birthDay = result.holder.birthDay,
                birthMonth = result.holder.birthMonth,
                includeBirthMonthNumber = false
            )

            if (remoteTestResult.result.negativeResult && testResultUtil.isValid(
                    sampleDate = remoteTestResult.result.sampleDate,
                    validitySeconds = TimeUnit.HOURS.toSeconds(
                        cachedAppConfigUseCase.getCachedAppConfigMaxValidityHours().toLong()
                    )
                )
            ) {
                TestResult.NegativeTestResult(
                    remoteTestResult,
                    personalDetails,
                    signedResponseWithTestResult
                )
            } else {
                TestResult.NoNegativeTestResult
            }
        } catch (ex: HttpException) {
            Timber.e(ex, "Server error while getting test result")
            TestResult.ServerError(ex.code())
        } catch (ex: IOException) {
            Timber.e(ex, "Network error while getting test result")
            TestResult.NetworkError
        }
    }

    suspend fun signTestResult(
        signedResponseWithTestResult: SignedResponseWithModel<RemoteTestResult>
    ): SignedTestResult {
        try {
            // Persist encrypted test result
            val remoteNonce = coronaCheckRepository.remoteNonce()
            val commitmentMessage = commitmentMessageUseCase.json(
                nonce = remoteNonce.nonce
            )
            Timber.i("Received commitment message $commitmentMessage")

            val testIsm = coronaCheckRepository.getTestIsm(
                test = signedResponseWithTestResult.rawResponse.toString(Charsets.UTF_8),
                sToken = remoteNonce.sToken,
                icm = commitmentMessage
            )
            when (testIsm) {
                is TestIsmResult.Success -> {
                    Timber.i("Received test ism json ${testIsm.body}")

                    val credentials = createCredentialUseCase.get(
                        secretKeyJson = secretKeyUseCase.json(),
                        testIsmBody = testIsm.body
                    )

                    val credentialsVerified = try {
                        testResultAttributesUseCase.get(credentials)
                        true
                    } catch (e: Exception) {
                        false
                    }

                    return if (credentialsVerified) {
                        SignedTestResult.Complete(credentials)
                    } else {
                        SignedTestResult.CouldNotVerifyCredentials
                    }
                }
                is TestIsmResult.Error -> {
                    return if (testIsm.responseError.code == ResponseError.CODE_ALREADY_SIGNED) {
                        SignedTestResult.AlreadySigned
                    } else {
                        SignedTestResult.ServerError(testIsm.httpCode, testIsm.responseError.code)
                    }
                }
            }
        } catch (ex: HttpException) {
            return SignedTestResult.ServerError(ex.code())
        } catch (ex: IOException) {
            return SignedTestResult.NetworkError
        }
    }
}

sealed class TestResult {
    object NoNegativeTestResult : TestResult()
    data class NegativeTestResult(
        val remoteTestResult: RemoteTestResult,
        val personalDetails: PersonalDetails,
        val signedResponseWithTestResult: SignedResponseWithModel<RemoteTestResult>
    ) : TestResult()

    object Pending : TestResult()
    object InvalidToken : TestResult()
    object VerificationRequired : TestResult()
    data class ServerError(val httpCode: Int) : TestResult()
    object NetworkError : TestResult()
}

sealed class SignedTestResult {
    data class Complete(val credentials: String) : SignedTestResult()
    object AlreadySigned : SignedTestResult()
    data class ServerError(val httpCode: Int, val errorCode: Int? = null) : SignedTestResult()
    object NetworkError : SignedTestResult()
    object CouldNotVerifyCredentials : SignedTestResult()
}
