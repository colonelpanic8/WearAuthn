package me.henneke.wearauthn.companion

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import me.henneke.wearauthn.companion.databinding.MainActivityBinding
import me.henneke.wearauthn.companion.ui.main.MainFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: MainActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, MainFragment.newInstance())
                .commitNow()
        }
    }
}
