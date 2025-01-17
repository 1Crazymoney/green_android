package com.blockstream.green.ui.wallet

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.blockstream.green.R
import com.blockstream.green.databinding.AddAccountFragmentBinding
import com.blockstream.green.ui.WalletFragment
import com.blockstream.green.ui.overview.OverviewFragment
import com.blockstream.green.utils.errorDialog
import com.blockstream.green.utils.hideKeyboard
import com.blockstream.green.utils.setNavigationResult
import com.greenaddress.Bridge
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AddAccountFragment : WalletFragment<AddAccountFragmentBinding>(
    layout = R.layout.add_account_fragment,
    menuRes = 0
) {
    val args: AddAccountFragmentArgs by navArgs()

    override val wallet by lazy { args.wallet }

    override val isAdjustResize: Boolean = true

    @Inject
    lateinit var viewModelFactory: AddAccountViewModel.AssistedFactory
    val viewModel: AddAccountViewModel by viewModels {
        AddAccountViewModel.provideFactory(viewModelFactory, wallet, args.accountType)
    }

    override fun onViewCreatedGuarded(view: View, savedInstanceState: Bundle?) {
        binding.vm = viewModel

        binding.buttonContinue.setOnClickListener {
            hideKeyboard()
            viewModel.createAccount()
        }

        viewModel.accountCreated.observe(viewLifecycleOwner) {
            setNavigationResult(result = it.pointer, key = OverviewFragment.ADD_NEW_ACCOUNT, destinationId = R.id.overviewFragment)
            findNavController().popBackStack(R.id.overviewFragment, false)
        }

        viewModel.onError.observe(viewLifecycleOwner) {
            it.getContentIfNotHandledOrReturnNull()?.let {
                errorDialog(it)
            }
        }
    }

    override fun getWalletViewModel(): AbstractWalletViewModel = viewModel
}