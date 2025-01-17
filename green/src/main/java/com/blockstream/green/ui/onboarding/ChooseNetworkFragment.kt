package com.blockstream.green.ui.onboarding

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.blockstream.gdk.GreenWallet
import com.blockstream.gdk.data.Network
import com.blockstream.green.R
import com.blockstream.green.data.OnboardingOptions
import com.blockstream.green.databinding.ChooseNetworkFragmentBinding
import com.blockstream.green.ui.ComingSoonBottomSheetDialogFragment
import com.blockstream.green.ui.items.NetworkListItem
import com.blockstream.green.ui.items.TitleExpandableListItem
import com.blockstream.green.utils.isDevelopmentFlavor
import com.mikepenz.fastadapter.GenericItem
import com.mikepenz.fastadapter.adapters.FastItemAdapter
import com.mikepenz.fastadapter.expandable.getExpandableExtension
import com.mikepenz.fastadapter.ui.utils.StringHolder
import com.mikepenz.itemanimators.SlideDownAlphaAnimator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ChooseNetworkFragment :
    AbstractOnboardingFragment<ChooseNetworkFragmentBinding>(
        R.layout.choose_network_fragment,
        menuRes = 0
    ) {

    @Inject
    lateinit var greenWallet: GreenWallet

    private val args: ChooseNetworkFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        options = args.onboardingOptions

        val fastItemAdapter = createNetworkAdapter()

        fastItemAdapter.onClickListener = { _, _, item: GenericItem, _ ->
            when (item) {
                is NetworkListItem -> {
                    options?.apply {
                        if(isRestoreFlow){
                            val newOptions = createCopyForNetwork(greenWallet, item.network, isSingleSig)

                            if(newOptions.network?.isMultisig == true || newOptions.isSinglesigNetworkEnabledForBuildFlavor(requireContext())){
                                navigate(newOptions)
                            }else{
                                ComingSoonBottomSheetDialogFragment().also {
                                    it.show(childFragmentManager, it.toString())
                                }
                            }
                        }else{
                            navigate(copy(networkType = item.network))
                        }
                    }
                    true
                }
                else -> false
            }
        }

        binding.recycler.apply {
            layoutManager = LinearLayoutManager(context)
            itemAnimator = SlideDownAlphaAnimator()
            adapter = fastItemAdapter
        }
    }

    private fun createNetworkAdapter(): FastItemAdapter<GenericItem> {
        val fastItemAdapter = FastItemAdapter<GenericItem>()
        fastItemAdapter.getExpandableExtension()

        fastItemAdapter.add(NetworkListItem(Network.GreenMainnet,"Bitcoin", getCaption("mainnet")))
        fastItemAdapter.add(NetworkListItem(Network.GreenLiquid, "Liquid", getCaption("liquid")))

        if(settingsManager.getApplicationSettings().testnet) {
            val expandable = TitleExpandableListItem(StringHolder(R.string.id_additional_networks))
            expandable.subItems.add(
                NetworkListItem(
                    Network.GreenTestnet,
                    "Testnet",
                    getCaption("testnet")
                )
            )

            if (isDevelopmentFlavor()) {
                expandable.subItems.add(
                    NetworkListItem(
                        Network.GreenTestnetLiquid,
                        "Testnet Liquid",
                        getCaption("testnet-liquid")
                    )
                )
            }

            fastItemAdapter.add(expandable)
        }

        return fastItemAdapter
    }

    private fun getCaption(network: String): String {
        return when (network) {
            "mainnet" -> getString(R.string.id_bitcoin_is_the_worlds_leading)
            "liquid" -> getString(R.string.id_the_liquid_network_is_a_bitcoin)
            else -> ""
        }
    }

    private fun navigate(options: OnboardingOptions) {
        if(options.isRestoreFlow){
            navigate(
                ChooseNetworkFragmentDirections.actionChooseNetworkFragmentToChooseRecoveryPhraseFragment(
                    options
                )
            )
        }else {
            navigate(
                ChooseNetworkFragmentDirections.actionChooseNetworkFragmentToChooseSecurityFragment(
                    options
                )
            )
        }
    }
}
