package com.ounben.amaradio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ounben.amaradio.interfaces.IFragmentRefreshable
import com.ounben.amaradio.ui.AMARadioTheme
import com.ounben.amaradio.ui.ServerInfoScreen
import com.ounben.amaradio.ui.ServerInfoViewModel

class FragmentServerInfo : Fragment(), IFragmentRefreshable {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                AMARadioTheme {
                    val viewModel: ServerInfoViewModel = viewModel()
                    ServerInfoScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun refresh() {
        // Need to bridge to ViewModel
    }
}
