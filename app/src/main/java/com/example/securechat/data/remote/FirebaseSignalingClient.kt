package com.example.securechat.data.remote

import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSignalingClient @Inject constructor() {
    private val database: DatabaseReference = FirebaseDatabase.getInstance().getReference("calls")

    fun sendOffer(callId: String, sdp: String) {
        database.child(callId).child("offer").setValue(
            mapOf("type" to "offer", "sdp" to sdp)
        )
    }

    fun sendAnswer(callId: String, sdp: String) {
        database.child(callId).child("answer").setValue(
            mapOf("type" to "answer", "sdp" to sdp)
        )
    }

    fun sendIceCandidate(callId: String, candidate: String, sdpMid: String, sdpMLineIndex: Int, isCaller: Boolean) {
        val role = if (isCaller) "caller" else "callee"
        val candidateMap = mapOf(
            "candidate" to candidate,
            "sdpMid" to sdpMid,
            "sdpMLineIndex" to sdpMLineIndex
        )
        database.child(callId).child("candidates").child(role).push().setValue(candidateMap)
    }

    fun listenForAnswer(callId: String): Flow<String?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val sdp = snapshot.child("sdp").getValue(String::class.java)
                    trySend(sdp)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        val ref = database.child(callId).child("answer")
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun listenForOffer(callId: String): Flow<String?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val sdp = snapshot.child("sdp").getValue(String::class.java)
                    trySend(sdp)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        val ref = database.child(callId).child("offer")
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    data class IceCandidateModel(val candidate: String, val sdpMid: String, val sdpMLineIndex: Int)

    fun listenForIceCandidates(callId: String, isCaller: Boolean): Flow<IceCandidateModel> = callbackFlow {
        val role = if (isCaller) "callee" else "caller" // listen to the other side's candidates
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val candidate = snapshot.child("candidate").getValue(String::class.java)
                val sdpMid = snapshot.child("sdpMid").getValue(String::class.java)
                val sdpMLineIndex = snapshot.child("sdpMLineIndex").getValue(Long::class.java)?.toInt()
                
                if (candidate != null && sdpMid != null && sdpMLineIndex != null) {
                    trySend(IceCandidateModel(candidate, sdpMid, sdpMLineIndex))
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        val ref = database.child(callId).child("candidates").child(role)
        ref.addChildEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
