package com.ounben.amaradio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class FragmentAbout : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.layout_about, null)

        val aTextVersion = view.findViewById<TextView>(R.id.about_version)
        if (aTextVersion != null) {
            var version = getString(R.string.version_name)
            val gitHash = getString(R.string.git_hash)
            val buildDate = getString(R.string.build_date)

            if (gitHash.isNotEmpty()) {
                version += " (git $gitHash)"
            }

            aTextVersion.text = resources.getString(R.string.about_version, "$version $buildDate")
        }

        return view
    }
}
