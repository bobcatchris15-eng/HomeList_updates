package com.example.mesh

interface MeshTransport {
    suspend fun start()
    suspend fun stop()
    suspend fun localEndpointId(): String
    suspend fun exchangeGossip(peerEndpointId: String, request: GossipRequestEnvelope): GossipResponseEnvelope
}
