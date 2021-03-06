/*
Copyright (c) 2013, Felix Ableitner
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the <organization> nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.github.nutomic.controldlna;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.teleal.cling.android.AndroidUpnpService;
import org.teleal.cling.android.AndroidUpnpServiceImpl;
import org.teleal.cling.model.action.ActionInvocation;
import org.teleal.cling.model.message.UpnpResponse;
import org.teleal.cling.model.meta.Device;
import org.teleal.cling.model.meta.Service;
import org.teleal.cling.model.types.ServiceType;
import org.teleal.cling.support.contentdirectory.callback.Browse;
import org.teleal.cling.support.model.BrowseFlag;
import org.teleal.cling.support.model.DIDLContent;
import org.teleal.cling.support.model.container.Container;
import org.teleal.cling.support.model.item.Item;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import com.github.nutomic.controldlna.MainActivity.OnBackPressedListener;

/**
 * Shows a list of media servers, upon selecting one, allows browsing theur
 * directories.
 * 
 * @author Felix
 *
 */
public class ServerFragment extends ListFragment implements OnBackPressedListener {
	
	private final String TAG = "ServerFragment";
	
	private final String ROOT_DIRECTORY = "0";
	
	/**
	 * ListView adapter for showing a list of DLNA media servers.
	 */
	private DeviceArrayAdapter mServerAdapter;
	
	/**
	 * Reference to the media server of which folders are currently shown. 
	 * Null if media servers are shown.
	 */
	private Device<?, ?, ?> mCurrentServer;
	
	/**
	 * ListView adapter for showing a list of files/folders.
	 */
	private FileArrayAdapter mFileAdapter;

	/**
	 * Holds path to current directory on top, paths for higher directories 
	 * behind that.
	 */
	private Stack<String> mCurrentPath = new Stack<String>();
	
	/**
	 * Cling UPNP service. 
	 */
    private AndroidUpnpService mUpnpService;

    /**
     * Connection Cling to UPNP service.
     */
    private ServiceConnection mUpnpServiceConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder service) {
            mUpnpService = (AndroidUpnpService) service;
            Log.i(TAG, "Starting device search");
            mUpnpService.getRegistry().addListener(mServerAdapter);
            mUpnpService.getControlPoint().search();
            mServerAdapter.add(
            		mUpnpService.getControlPoint().getRegistry().getDevices());
        }

        public void onServiceDisconnected(ComponentName className) {
            mUpnpService = null;
        }
    };
    
	/**
	 * Initializes ListView adapters, launches Cling UPNP service.
	 */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
    	super.onActivityCreated(savedInstanceState);
    	mFileAdapter = new FileArrayAdapter(getActivity());

    	mServerAdapter = new DeviceArrayAdapter(
    			getActivity(), DeviceArrayAdapter.SERVER);
        setListAdapter(mServerAdapter);  

        getActivity().getApplicationContext().bindService(
            new Intent(getActivity(), AndroidUpnpServiceImpl.class),
            mUpnpServiceConnection,
            Context.BIND_AUTO_CREATE
        );     
    }

    /**
     * Closes Cling UPNP service.
     */
    @Override
	public void onDestroy() {
        super.onDestroy();
        if (mUpnpService != null)
            mUpnpService.getRegistry().removeListener(mServerAdapter);
        getActivity().getApplicationContext().unbindService(mUpnpServiceConnection);
    }
    
    /**
     * Enters directory browsing mode or enters a deeper level directory.
     */
    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
    	if (getListAdapter() == mServerAdapter) {
    		setListAdapter(mFileAdapter);
    		mCurrentServer = mServerAdapter.getItem(position);
    		getFiles(ROOT_DIRECTORY);
    	}
    	else if (getListAdapter() == mFileAdapter) {
    		if (mFileAdapter.getItem(position) instanceof Container) {
    			getFiles(((Container) mFileAdapter.getItem(position)).getId());    			
    		}
    		else {
    			List<Item> playlist = new ArrayList<Item>();
    			for (int i = 0; i < mFileAdapter.getCount(); i++) {
    				if (mFileAdapter.getItem(i) instanceof Item)
    					playlist.add((Item) mFileAdapter.getItem(i));
    			}
    			MainActivity activity = (MainActivity) getActivity();
    			activity.play(playlist, position);
    		}
    	}
    }
    
    /**
     * Opens a new directory and displays it.
     */
    private void getFiles(String directory) {
		mCurrentPath.push(directory);    	
    	getFiles();
    }
    
    /**
     * Displays the current directory on the ListView.
     */
    private void getFiles() {
    	Service<?, ?> service = mCurrentServer.findService(
    			new ServiceType("schemas-upnp-org", "ContentDirectory"));
		mUpnpService.getControlPoint().execute(new Browse(service, 
				mCurrentPath.peek(), BrowseFlag.DIRECT_CHILDREN) {
		
					@SuppressWarnings("rawtypes")
					@Override
					public void received(ActionInvocation actionInvocation, 
							final DIDLContent didl) {
						getActivity().runOnUiThread(new Runnable() {
							
							@Override
							public void run() {
								mFileAdapter.clear();
								for (Container c : didl.getContainers()) 
									mFileAdapter.add(c);
								for (Item i : didl.getItems())
									mFileAdapter.add(i);
							}
						});	
					}
		
					@Override
					public void updateStatus(Status status) {
					}
		
					@SuppressWarnings("rawtypes")
					@Override
					public void failure(ActionInvocation actionInvocation, 
							UpnpResponse operation,	String defaultMessage) {
						Log.w(TAG, "Failed to load directory contents: " + 
							defaultMessage);
					}
					
				});    	
    }
	
    /**
     * Handles back button press to traverse directories (while in directory 
     * browsing mode).
     */
	public boolean onBackPressed() {
    	if (getListAdapter() == mServerAdapter)
    		return false;
		mCurrentPath.pop();
		if (mCurrentPath.empty()) {
    		setListAdapter(mServerAdapter);
    		mCurrentServer = null;
		}
		else {
			getFiles();
		}
		return true;		
	}

}
