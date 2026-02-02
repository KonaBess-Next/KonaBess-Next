package com.ireddragonicy.konabessnext.ui.fragments

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.ireddragonicy.konabessnext.R
import com.ireddragonicy.konabessnext.repository.ChipRepository
// ChipInfo import removed
import com.ireddragonicy.konabessnext.model.Opp
import com.ireddragonicy.konabessnext.utils.DialogUtil
import com.ireddragonicy.konabessnext.viewmodel.GpuVoltViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GpuVoltFragment : Fragment() {

    private val viewModel: GpuVoltViewModel by viewModels()
    
    @javax.inject.Inject
    lateinit var chipRepository: ChipRepository
    private lateinit var contentContainer: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.background_light)) // Or theme color
        }

        // Toolbar
        val toolbar = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 16, 32, 16)
        }
        
        val btnBack = MaterialButton(requireContext()).apply {
            text = "Back"
            setIconResource(R.drawable.ic_arrow_back)
            setOnClickListener { parentFragmentManager.popBackStack() }
        }
        
        val btnSave = MaterialButton(requireContext()).apply {
            text = "Save"
            setIconResource(R.drawable.ic_save)
            setOnClickListener { viewModel.save() }
        }
        
        val btnAdd = MaterialButton(requireContext()).apply {
            text = "Add"
            // setIconResource(R.drawable.ic_add) // Use standard or text only if missing
            setOnClickListener { showAddDialog() }
        }
        
        toolbar.addView(btnBack)
        toolbar.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) }) // Spacer
        toolbar.addView(btnAdd)
        toolbar.addView(btnSave)
        
        root.addView(toolbar)

        // Scrollable Content
        val scrollView = ScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        contentContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }
        
        scrollView.addView(contentContainer)
        root.addView(scrollView)
        
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.opps.collectLatest { opps ->
                        rebuildList(opps)
                    }
                }
                launch {
                    viewModel.toastEvent.collectLatest { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun rebuildList(opps: List<Opp>) {
        contentContainer.removeAllViews()
        
        if (opps.isEmpty()) {
            contentContainer.addView(TextView(requireContext()).apply {
                text = "No Voltage Table entries found."
                gravity = Gravity.CENTER
                setPadding(32, 64, 32, 64)
            })
            return
        }

        opps.forEachIndexed { index, opp ->
            val card = MaterialCardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
                cardElevation = 4f
                radius = 16f
                setContentPadding(32, 16, 32, 16)
            }

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val info = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val freqText = TextView(requireContext()).apply {
                text = "${opp.frequency} Hz"
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            
            val voltText = TextView(requireContext()).apply {
                text = getLevelStr(opp.volt)
                textSize = 14f
            }

            info.addView(freqText)
            info.addView(voltText)
            
            row.addView(info)
            
            // Edit Button
            val btnEdit = MaterialButton(requireContext(), null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                text = "Edit"
                setOnClickListener { showEditDialog(index, opp) }
            }
            row.addView(btnEdit)
            
            // Delete Button
            val btnDelete = MaterialButton(requireContext(), null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                text = "Del"
                // setIconResource(R.drawable.ic_delete)
                setOnClickListener { viewModel.removeOpp(index) }
            }
            row.addView(btnDelete)

            card.addView(row)
            contentContainer.addView(card)
        }
    }
    
    private fun showEditDialog(index: Int, opp: Opp) {
         DialogUtil.showEditDialog(requireActivity(), 
            "Edit Frequency", 
            "Enter frequency in Hz", 
            opp.frequency.toString(), 
            InputType.TYPE_CLASS_NUMBER
        ) { text ->
             val newFreq = text.toLongOrNull()
             if (newFreq != null) {
                 // Then Volt
                 showVoltDialog(index, newFreq, opp.volt)
             }
        }
    }
    
    private fun showVoltDialog(index: Int, newFreq: Long, currentVolt: Long) {
        val levels = chipRepository.getLevelsForCurrentChip()
        val levelStrs = chipRepository.getLevelStringsForCurrentChip()
        
        var selectedIdx = 0
        for(i in levels.indices) {
            if (levels[i].toLong() == currentVolt) {
                selectedIdx = i
                break
            }
        }
        
        DialogUtil.showSingleChoiceDialog(requireActivity(),
            "Select Voltage",
            levelStrs,
            selectedIdx
        ) { dialog, which ->
             val newVolt = levels[which]
             viewModel.updateOpp(index, newFreq, newVolt.toLong())
             dialog.dismiss()
        }
    }
    
    private fun showAddDialog() {
         DialogUtil.showEditDialog(requireActivity(), 
            "Add Entry", 
            "Enter frequency in Hz", 
            "", 
            InputType.TYPE_CLASS_NUMBER
        ) { text ->
             val newFreq = text.toLongOrNull()
             if (newFreq != null) {
                 showVoltAddDialog(newFreq)
             }
        }
    }
    
    private fun showVoltAddDialog(freq: Long) {
        val levels = chipRepository.getLevelsForCurrentChip()
        val levelStrs = chipRepository.getLevelStringsForCurrentChip()
        
        DialogUtil.showSingleChoiceDialog(requireActivity(),
            "Select Voltage",
            levelStrs,
            0
        ) { dialog, which ->
             val newVolt = levels[which]
             viewModel.addOpp(freq, newVolt.toLong())
             dialog.dismiss()
        }
    }

    private fun getLevelStr(level: Long): String {
        try {
            val levels = chipRepository.getLevelsForCurrentChip()
            for (i in levels.indices) {
                if (levels[i].toLong() == level) return chipRepository.getLevelStringsForCurrentChip()[i] + " ($level)"
            }
        } catch (e: Exception) {}
        return "Level $level"
    }
}
