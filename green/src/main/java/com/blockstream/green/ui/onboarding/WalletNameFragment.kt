package com.blockstream.green.ui.onboarding

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.viewModels
import androidx.lifecycle.distinctUntilChanged
import androidx.navigation.fragment.navArgs
import com.blockstream.green.R
import com.blockstream.green.database.Wallet
import com.blockstream.green.databinding.WalletNameFragmentBinding
import com.blockstream.green.gdk.getGDKErrorCode
import com.blockstream.green.settings.SettingsManager
import com.blockstream.green.ui.dialogs.showTorSinglesigWarningIfNeeded
import com.blockstream.green.utils.errorDialog
import com.blockstream.green.utils.isDevelopmentFlavor
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WalletNameFragment :
    AbstractOnboardingFragment<WalletNameFragmentBinding>(
        R.layout.wallet_name_fragment,
        menuRes = 0
    ) {

    override val isAdjustResize: Boolean = true

    @Inject
    lateinit var viewModelFactory: WalletNameViewModel.AssistedFactory
    val viewModel: WalletNameViewModel by viewModels {
        WalletNameViewModel.provideFactory(viewModelFactory, args.onboardingOptions, args.restoreWallet)
    }
    private val args: WalletNameFragmentArgs by navArgs()

    private val onBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            // Prevent back
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.vm = viewModel

        options = args.onboardingOptions

        binding.buttonContinue.setOnClickListener {
            options?.let {
                if(it.isRestoreFlow){
                    viewModel.checkRecoveryPhrase(it.network!!, args.mnemonic, args.mnemonicPassword)
                }else{
                    navigateToPin()
                }
            }
        }

        binding.buttonSettings.setOnClickListener {
            navigate(WalletNameFragmentDirections.actionGlobalAppSettingsDialogFragment())
        }

        viewModel.onError.observe(viewLifecycleOwner){
            it?.getContentIfNotHandledOrReturnNull()?.let{ error ->
                errorDialog(error)
            }
        }

        viewModel.onProgress.observe(viewLifecycleOwner){
            setToolbarVisibility(!it)
            onBackCallback.isEnabled = it
        }

        viewModel.onEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                if(it is Wallet){
                    options?.let { options ->
                        navigate(WalletNameFragmentDirections.actionWalletNameFragmentToOnBoardingCompleteFragment(
                            onboardingOptions = options.copy(walletName = viewModel.getName()),
                            wallet = it)
                        )
                    }

                }else {
                    navigateToPin()
                }
            }
        }

        // Show Singlesig Tor warning
        settingsManager.getApplicationSettingsLiveData().distinctUntilChanged().observe(viewLifecycleOwner) {
            it?.let { applicationSettings ->
                options?.network?.let { network ->
                    if(applicationSettings.tor && !network.supportTorConnection){
                        showTorSinglesigWarningIfNeeded(settingsManager)
                    }
                }
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackCallback)
    }

    private fun navigateToPin(){
        options?.copy(walletName = viewModel.getName())?.let {
            navigate(WalletNameFragmentDirections.actionWalletNameFragmentToSetPinFragment(onboardingOptions = it, restoreWallet = args.restoreWallet, mnemonic = args.mnemonic))
        }
    }
}