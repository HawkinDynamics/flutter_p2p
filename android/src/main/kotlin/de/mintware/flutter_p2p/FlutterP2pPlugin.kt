/*
 * This file is part of the flutter_p2p package.
 *
 * Copyright 2019 by Julian Finkler <julian@mintware.de>
 *
 * For the full copyright and license information, please read the LICENSE
 * file that was distributed with this source code.
 *
 */

package de.mintware.flutter_p2p

import android.Manifest
import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import android.util.Log
import de.mintware.flutter_p2p.utility.EventChannelPool
import de.mintware.flutter_p2p.wifi_direct.WiFiDirectBroadcastReceiver
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.lang.reflect.Method
import java.util.HashMap
import android.net.wifi.WpsInfo
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.PluginRegistry

class FlutterP2pPlugin : MethodCallHandler, PluginRegistry.RequestPermissionsResultListener,
    ActivityAware, FlutterPlugin {

    private val REQUEST_ENABLE_LOCATION = 600
    private val CH_STATE_CHANGE = "bc/state-change"
    private val CH_PEERS_CHANGE = "bc/peers-change"
    private val CH_CON_CHANGE = "bc/connection-change"
    private val CH_DEVICE_CHANGE = "bc/this-device-change"
    private val CH_DISCOVERY_CHANGE = "bc/discovery-change"
    private val CH_SOCKET_READ = "socket/read"

    private var activity: Activity? = null
    private var context: Context? = null

    private var receiver: WiFiDirectBroadcastReceiver? = null
    private var hasRegistered: Boolean? = false


    private lateinit var eventPool: EventChannelPool;
    private lateinit var intentFilter: IntentFilter
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var manager: WifiP2pManager
    private lateinit var permissionResult: Result

    fun registerWith(registrar: Registrar) {
        val channel = MethodChannel(registrar.messenger(), "de.mintware.flutter_p2p/flutter_p2p")
        setupEventPool(registrar.messenger())
        channel.setMethodCallHandler(this)
    }

    private fun setupEventPool(messenger : BinaryMessenger) {
        eventPool = EventChannelPool(messenger)
        eventPool.register(CH_STATE_CHANGE)
        eventPool.register(CH_PEERS_CHANGE)
        eventPool.register(CH_CON_CHANGE)
        eventPool.register(CH_DEVICE_CHANGE)
        eventPool.register(CH_SOCKET_READ)
        eventPool.register(CH_DISCOVERY_CHANGE)
    }

    private fun setupIntentFilters() {
        intentFilter = IntentFilter()
        intentFilter.apply {
            // Indicates a change in the Wi-Fi P2P status.
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            // Indicates a change in the list of available peers.
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            // Indicates the state of Wi-Fi P2P connectivity has changed.
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            // Indicates this device'base details have changed.
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            // Indicates the state of peer discovery has changed
            addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
        }
    }

    private fun setupWifiP2pManager() {
        manager = context?.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(context, Looper.getMainLooper(), null)
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    private fun requestLocationPermission(call: MethodCall, result: Result) {
        permissionResult = result
        val perm = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        activity?.requestPermissions(perm, REQUEST_ENABLE_LOCATION)
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    private fun isLocationPermissionGranted(call: MethodCall, result: Result) {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        result.success(PackageManager.PERMISSION_GRANTED == context?.checkSelfPermission(permission))
    }

    /**
     * Subscribe to WiFi Events
     *
     * @param call The Method call
     * @param result The Method result
     */
    @Suppress("unused", "UNUSED_PARAMETER")
    fun register(call: MethodCall, result: Result) {
        if (hasRegistered == true) {
            result.success(false)
            return
        }

        setupIntentFilters()
        setupWifiP2pManager()

        receiver = WiFiDirectBroadcastReceiver(
                manager,
                channel,
                eventPool.getHandler(CH_STATE_CHANGE).sink,
                eventPool.getHandler(CH_PEERS_CHANGE).sink,
                eventPool.getHandler(CH_CON_CHANGE).sink,
                eventPool.getHandler(CH_DEVICE_CHANGE).sink,
                eventPool.getHandler(CH_DISCOVERY_CHANGE).sink
        )
        context?.registerReceiver(receiver, intentFilter)
        result.success(true)
    }

    /**
     * Unsubscribe from WiFi Events
     *
     * @param call The Method call
     * @param result The Method result
     */
    @Suppress("unused", "UNUSED_PARAMETER")
    fun unregister(call: MethodCall, result: Result) {
        if (receiver == null) {
            result.success(false)
            return
        }

        context?.unregisterReceiver(receiver)
        result.success(true)
    }


    /**
     * Start discovering WiFi devices
     *
     * @param call The Method call
     * @param result The Method result
     */
    @Suppress("unused", "UNUSED_PARAMETER")
    fun discover(call: MethodCall, result: Result) {
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                result.success(true)
            }

            override fun onFailure(reasonCode: Int) {
                result.error(reasonCode.toString(), null, null)
            }
        })
    }

    /**
     * Stop discovering WiFi devices
     *
     * @param call The Method call
     * @param result The Method result
     */
    @Suppress("unused", "UNUSED_PARAMETER")
    fun stopDiscover(call: MethodCall, result: Result) {
        manager.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                result.success(true)
            }

            override fun onFailure(reasonCode: Int) {
                result.error(reasonCode.toString(), null, null)
            }
        })
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun connect(call: MethodCall, result: Result) {
        val device = Protos.WifiP2pDevice.parseFrom(call.argument<ByteArray>("payload"))

        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC
            groupOwnerIntent = 14
        }

        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                result.success(true)
            }

            override fun onFailure(reasonCode: Int) {
                result.error(reasonCode.toString(), null, null)
            }
        })
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun cancelConnect(call: MethodCall, result: Result) {
        manager.cancelConnect(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                result.success(true)
            }

            override fun onFailure(reasonCode: Int) {
                result.error(reasonCode.toString(), null, null)
            }
        })
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun removeGroup(call: MethodCall, result: Result) {
        manager.requestGroupInfo(channel) { group ->
            if (group != null) {
                manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        result.success(true)
                    }

                    override fun onFailure(reasonCode: Int) {
                        result.error(reasonCode.toString(), null, null)
                    }
                })
            } else {
                // signal success as the device is not currently a member of a group
                result.success(true)
            }
        }
    }

    private val methodMap = HashMap<String, Method>()

    override fun onMethodCall(call: MethodCall, result: Result) {
        if (methodMap.isEmpty()) {
            fetchMethods()
        }

        val method = methodMap[call.method]
        if (null == method) {
            result.notImplemented()
            return
        }

        Log.v(TAG, "Method: " + call.method)
        val args = arrayOfNulls<Any>(2)
        args[0] = call
        args[1] = result

        try {
            method.invoke(this, *args)
        } catch (e: Exception) {
            result.error(call.method, e.message, e)
        }
    }

    private fun fetchMethods() {
        val c = this::class.java
        val m = c.declaredMethods

        for (method in m) {
            methodMap[method.name] = method
        }
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {
        when (requestCode) {
            REQUEST_ENABLE_LOCATION -> {
                Log.v(TAG, "JABRONI")
                if (null != grantResults) {
                    val permissionGranted = grantResults.isNotEmpty() &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED
                    permissionResult.success(permissionGranted)
                }
            }
        }
        return true
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        val channel = MethodChannel(binding.binaryMessenger, "de.mintware.flutter_p2p/flutter_p2p")
        setupEventPool(binding.binaryMessenger)
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = null
        activity = null
    }
}
