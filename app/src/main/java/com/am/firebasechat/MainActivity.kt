package com.am.firebasechat

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.AuthUI.IdpConfig
import com.firebase.ui.auth.AuthUI.IdpConfig.EmailBuilder
import com.firebase.ui.auth.AuthUI.IdpConfig.GoogleBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuth.AuthStateListener
import com.google.firebase.database.*


class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    private val ANONYMOUS = "anonymous"
    private val DEFAULT_MSG_LENGTH_LIMIT = 1000
    private val RC_SIGN_IN = 1


    private var mMessageListView: ListView? = null
    private var mMessageAdapter: MessageAdapter? = null
    private var mProgressBar: ProgressBar? = null
    private var mPhotoPickerButton: ImageButton? = null
    private var mMessageEditText: EditText? = null
    private var mSendButton: Button? = null

    private var mUsername: String? = null

    //the entrypoint for the app to access the database
    private lateinit var mFirebaseDatabase: FirebaseDatabase
    //references a specific part of the database - in my case, referencing the
    // messages portion of the database
    private lateinit var mMessagesDatabaseReference: DatabaseReference
    //for reading messages from firebase db
    // TODO fix nullable attribute
    private var mChildEventListener: ChildEventListener? = null
    private lateinit var mFirebaseAuth: FirebaseAuth
    private var mAuthStateListener: AuthStateListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mUsername = ANONYMOUS

        mFirebaseDatabase = FirebaseDatabase.getInstance()
        mMessagesDatabaseReference = mFirebaseDatabase.reference.child("messages")
        mFirebaseAuth = FirebaseAuth.getInstance();


        // Initialize references to views
        mProgressBar = findViewById<View>(R.id.progressBar) as ProgressBar
        mMessageListView = findViewById<View>(R.id.messageListView) as ListView
        mPhotoPickerButton = findViewById<View>(R.id.photoPickerButton) as ImageButton
        mMessageEditText = findViewById<View>(R.id.messageEditText) as EditText
        mSendButton = findViewById<View>(R.id.sendButton) as Button

        // Initialize message ListView and its adapter
        val friendlyMessages: List<FriendlyMessage> = ArrayList()
        mMessageAdapter = MessageAdapter(this, R.layout.item_message, friendlyMessages)
        mMessageListView!!.adapter = mMessageAdapter

        // Initialize progress bar
        mProgressBar!!.visibility = ProgressBar.INVISIBLE

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton!!.setOnClickListener {
            // TODO: Fire an intent to show an image picker
        }

        // Enable Send button when there's text to send
        mMessageEditText!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                mSendButton!!.isEnabled = charSequence.toString().trim { it <= ' ' }.isNotEmpty()
            }

            override fun afterTextChanged(editable: Editable) {}
        })
        mMessageEditText!!.filters = arrayOf<InputFilter>(LengthFilter(DEFAULT_MSG_LENGTH_LIMIT))

        // Send button sends a message and clears the EditText
        mSendButton!!.setOnClickListener {
            val friendlyMessage = FriendlyMessage(
                mMessageEditText!!.text.toString(),
                mUsername!!, ""
            )
            mMessageEditText!!.setText("")
            mMessagesDatabaseReference.push().setValue(friendlyMessage);
        }

        mAuthStateListener = AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                //user is signedin
                onSignedInInitialize(user.getDisplayName());
            } else {
                //user is signed out
                // Choose authentication providers
                val providers: List<IdpConfig> = listOf(
                    EmailBuilder().build(),
                    GoogleBuilder().build()
                )

                // Create and launch sign-in intent
                startActivityForResult(
                    AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setIsSmartLockEnabled(false)
                        .setAvailableProviders(providers)
                        .build(),
                    RC_SIGN_IN
                )
            }
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                // Sign-in succeeded, set up the UI
                Toast.makeText(this, "Signed in!", Toast.LENGTH_SHORT).show()
            } else if (resultCode == RESULT_CANCELED) {
                // Sign in was canceled by the user, finish the activity
                Toast.makeText(this, "Sign in canceled", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mFirebaseAuth.addAuthStateListener(mAuthStateListener)
    }

    override fun onPause() {
        super.onPause()
        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener)
        }
        mMessageAdapter?.clear();
        detachDatabaseReadListener();
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.sign_out_menu -> {
                AuthUI.getInstance().signOut(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun onSignedInInitialize(username: String) {
        mUsername = username
        attachDatabaseReadListener()
    }

    private fun onSignedOutCleanup() {
        mUsername = ANONYMOUS
        mMessageAdapter!!.clear()
        detachDatabaseReadListener()
    }

    private fun attachDatabaseReadListener() {
        if (mChildEventListener == null) {
            mChildEventListener = object : ChildEventListener {
                override fun onChildAdded(dataSnapshot: DataSnapshot, s: String?) {
                    val friendlyMessage = dataSnapshot.getValue(
                        FriendlyMessage::class.java
                    )
                    mMessageAdapter!!.add(friendlyMessage)
                }

                override fun onChildChanged(dataSnapshot: DataSnapshot, s: String?) {}
                override fun onChildRemoved(dataSnapshot: DataSnapshot) {}
                override fun onChildMoved(dataSnapshot: DataSnapshot, s: String?) {}
                override fun onCancelled(databaseError: DatabaseError) {}
            }
            mMessagesDatabaseReference.addChildEventListener(mChildEventListener!!)
        }
    }

    private fun detachDatabaseReadListener() {
        if (mChildEventListener != null) {
            mMessagesDatabaseReference.removeEventListener(mChildEventListener!!)
            mChildEventListener = null
        }
    }
}