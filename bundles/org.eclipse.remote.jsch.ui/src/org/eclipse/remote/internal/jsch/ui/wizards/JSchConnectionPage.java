/**
 * Copyright (c) 2013 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - Initial Implementation
 *
 */
package org.eclipse.remote.internal.jsch.ui.wizards;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.remote.core.IRemoteConnection;
import org.eclipse.remote.core.IRemoteConnectionManager;
import org.eclipse.remote.core.RemoteServices;
import org.eclipse.remote.core.exception.RemoteConnectionException;
import org.eclipse.remote.internal.jsch.core.Activator;
import org.eclipse.remote.internal.jsch.core.JSchConnection;
import org.eclipse.remote.internal.jsch.core.JSchConnectionAttributes;
import org.eclipse.remote.internal.jsch.core.JSchConnectionWorkingCopy;
import org.eclipse.remote.internal.jsch.ui.messages.Messages;
import org.eclipse.remote.ui.widgets.RemoteConnectionWidget;
import org.eclipse.remote.ui.widgets.RemoteFileWidget;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.forms.events.ExpansionEvent;
import org.eclipse.ui.forms.events.IExpansionListener;
import org.eclipse.ui.forms.widgets.ExpandableComposite;

public class JSchConnectionPage extends WizardPage {
	private class DataModifyListener implements ModifyListener {
		@Override
		public synchronized void modifyText(ModifyEvent e) {
			validateFields();
			getContainer().updateButtons();
		}
	}

	private Text fConnectionName;
	private Button fPasswordButton;
	private Button fPublicKeyButton;
	private Text fHostText;
	private Text fUserText;
	private Text fPasswordText;
	private Text fPassphraseText;
	private Text fPortText;
	private Text fTimeoutText;
	private RemoteFileWidget fFileWidget;

	private String fInitialName = "Remote Host"; //$NON-NLS-1$
	private Set<String> fInvalidConnectionNames;
	private final Map<String, String> fInitialAttributes = new HashMap<String, String>();
	private JSchConnectionWorkingCopy fConnection;

	private final IRemoteConnectionManager fConnectionManager;

	private final DataModifyListener fDataModifyListener = new DataModifyListener();
	private RemoteConnectionWidget fProxyConnectionWidget;
	private Text fProxyCommandText;
	private static final String PREFS_PAGE_ID_NET_PROXY = "org.eclipse.ui.net.NetPreferences"; //$NON-NLS-1$

	public JSchConnectionPage(IRemoteConnectionManager connMgr) {
		super(Messages.JSchNewConnectionPage_New_Connection);
		fConnectionManager = connMgr;
		setPageComplete(false);
	}

	/**
	 * Create controls for the bottom (hideable) advanced composite
	 *
	 * @param mold
	 *
	 */
	private void createAdvancedControls(final Composite parent) {
		ExpandableComposite expComp = new ExpandableComposite(parent, ExpandableComposite.TWISTIE);
		expComp.setText(Messages.JSchNewConnectionPage_Advanced);
		expComp.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		expComp.setExpanded(false);
		expComp.addExpansionListener(new IExpansionListener() {

			@Override
			public void expansionStateChanged(ExpansionEvent e) {
				for (int i = 0; i < 2; i++) { // sometimes the size compute isn't correct on first try
					Point newSize = parent.computeSize(SWT.DEFAULT, SWT.DEFAULT);
					Point currentSize = parent.getSize();
					int deltaY = newSize.y - currentSize.y;
					Point shellSize = getShell().getSize();
					shellSize.y += deltaY;
					getShell().setSize(shellSize);
					getShell().layout(true, true);
				}
			}

			@Override
			public void expansionStateChanging(ExpansionEvent e) {
				// Ignore
			}
		});

		Composite advancedComp = new Composite(expComp, SWT.NONE);
		advancedComp.setLayout(new GridLayout(1, false));
		advancedComp.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		Group settingsComp = new Group(advancedComp, SWT.NONE);
		settingsComp.setText(Messages.JSchConnectionPage_Settings0);
		settingsComp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		settingsComp.setLayout(new GridLayout(2, false));

		Label portLabel = new Label(settingsComp, SWT.NONE);
		portLabel.setText(Messages.JSchNewConnectionPage_Port);
		portLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		fPortText = new Text(settingsComp, SWT.BORDER | SWT.SINGLE);
		fPortText.setText(Integer.toString(JSchConnection.DEFAULT_PORT));
		fPortText.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
		setTextFieldWidthInChars(fPortText, 5);

		Label timeoutLabel = new Label(settingsComp, SWT.NONE);
		timeoutLabel.setText(Messages.JSchNewConnectionPage_Timeout);
		timeoutLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		fTimeoutText = new Text(settingsComp, SWT.BORDER | SWT.SINGLE);
		fTimeoutText.setText(Integer.toString(JSchConnection.DEFAULT_TIMEOUT));
		fTimeoutText.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
		setTextFieldWidthInChars(fTimeoutText, 5);

		Group proxyComp = new Group(advancedComp, SWT.NONE);
		proxyComp.setText(Messages.JSchConnectionPage_Proxy);
		proxyComp.setLayout(new GridLayout(1, false));
		proxyComp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		createProxyControls(proxyComp);

		expComp.setClient(advancedComp);
	}

	private void createAuthControls(Composite parent) {
		Composite controls = new Composite(parent, SWT.NONE);
		controls.setLayout(new GridLayout(2, false));
		controls.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

		Label hostLabel = new Label(controls, SWT.NONE);
		hostLabel.setText(Messages.JSchNewConnectionPage_Host);
		hostLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		fHostText = new Text(controls, SWT.BORDER | SWT.SINGLE);
		fHostText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		Label userLabel = new Label(controls, SWT.NONE);
		userLabel.setText(Messages.JSchNewConnectionPage_User);
		userLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		fUserText = new Text(controls, SWT.BORDER | SWT.SINGLE);
		fUserText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		// User option box
		fPasswordButton = new Button(controls, SWT.RADIO);
		fPasswordButton.setText(Messages.JSchNewConnectionPage_Password_based_authentication);
		fPasswordButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1));
		fPasswordButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				validateFields();
				updateEnablement();
			}
		});

		// Password field
		Label passwordLabel = new Label(controls, SWT.NONE);
		passwordLabel.setText(Messages.JSchNewConnectionPage_Password);
		passwordLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		fPasswordText = new Text(controls, SWT.BORDER | SWT.SINGLE | SWT.PASSWORD);
		fPasswordText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		// Key option box
		fPublicKeyButton = new Button(controls, SWT.RADIO);
		fPublicKeyButton.setText(Messages.JSchNewConnectionPage_Public_key_based_authentication);
		fPublicKeyButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1));
		fPublicKeyButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				validateFields();
				updateEnablement();
			}
		});

		// Key file selection
		fFileWidget = new RemoteFileWidget(controls, SWT.NONE, 0, null, ""); //$NON-NLS-1$
		fFileWidget.setConnection(RemoteServices.getLocalServices().getConnectionManager()
				.getConnection(IRemoteConnectionManager.LOCAL_CONNECTION_NAME));
		fFileWidget.setLabel(Messages.JSchNewConnectionPage_File_with_private_key);
		fFileWidget.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		// Passphrase field
		Label passphraseLabel = new Label(controls, SWT.NONE);
		passphraseLabel.setText(Messages.JSchNewConnectionPage_Passphrase);
		passphraseLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		fPassphraseText = new Text(controls, SWT.BORDER | SWT.SINGLE | SWT.PASSWORD);
		fPassphraseText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		fPasswordButton.setSelection(true);
		fPublicKeyButton.setSelection(false);
		controls.setTabList(new Control[] { fHostText, fUserText, fPasswordButton, fPasswordText, fPublicKeyButton, fFileWidget,
				fPassphraseText });
	}

	@Override
	public void createControl(Composite parent) {
		if (fConnection == null) {
			setDescription(Messages.JSchNewConnectionPage_New_connection_properties);
			setTitle(Messages.JSchNewConnectionPage_New_Connection);
			setMessage(Messages.JSchConnectionPage_Please_enter_name_for_connection);
		} else {
			setDescription(Messages.JSchConnectionPage_Edit_properties_of_an_existing_connection);
			setTitle(Messages.JSchConnectionPage_Edit_Connection);
		}
		setErrorMessage(null);

		GridLayout topLayout = new GridLayout(2, false);
		final Composite topControl = new Composite(parent, SWT.NONE);
		setControl(topControl);
		topControl.setLayout(topLayout);

		Label label = new Label(topControl, SWT.NONE);
		label.setText(Messages.JSchNewConnectionPage_Connection_name);
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

		fConnectionName = new Text(topControl, SWT.BORDER | SWT.SINGLE);
		fConnectionName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		fConnectionName.setEnabled(fConnection == null);

		final Group authGroup = new Group(topControl, SWT.NONE);
		authGroup.setText(Messages.JSchNewConnectionPage_Host_information);
		authGroup.setLayout(new GridLayout(1, false));
		authGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));

		createAuthControls(authGroup);
		createAdvancedControls(authGroup);

		loadValues();
		/*
		 * Register listeners after loading values so we don't trigger listeners
		 */
		registerListeners();

		if (fConnection != null) {
			validateFields();
		}

		updateEnablement();
	}

	/**
	 * Create controls for the bottom (hideable) proxy composite
	 *
	 * @param mold
	 *
	 */
	private void createProxyControls(final Composite proxyComp) {
		Label lblConnection = new Label(proxyComp, SWT.WRAP);
		lblConnection.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		lblConnection.setText(Messages.JSchConnectionPage_SelectConnection);

		fProxyConnectionWidget = new RemoteConnectionWidget(proxyComp, SWT.NONE, null, 0, null);

		Label lblCommand = new Label(proxyComp, SWT.WRAP);
		lblCommand.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		lblCommand.setText(Messages.JSchConnectionPage_SelectCommand);

		fProxyCommandText = new Text(proxyComp, SWT.BORDER);
		fProxyCommandText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Link link = new Link(proxyComp, SWT.WRAP);
		final GridData linkLayoutData = new GridData(GridData.FILL_HORIZONTAL);
		link.setLayoutData(linkLayoutData);
		link.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				PreferenceDialog dlg = PreferencesUtil.createPreferenceDialogOn(getShell(), PREFS_PAGE_ID_NET_PROXY,
						new String[] { PREFS_PAGE_ID_NET_PROXY }, null);
				dlg.open();
			}
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				// ignore
			}
		});

		linkLayoutData.widthHint = 400;
		link.setText(Messages.JSchConnectionPage_Help);
	}

	public JSchConnectionWorkingCopy getConnection() {
		return fConnection;
	}

	/**
	 * Check if the connection name is invalid. This only applies to new connections (when fConnection is null).
	 *
	 * @param name
	 *            connection name
	 * @return true if the name is invalid, false otherwise
	 */
	private boolean isInvalidName(String name) {
		if (fConnection == null) {
			if (fInvalidConnectionNames == null) {
				return fConnectionManager.getConnection(name) != null;
			}
			return fInvalidConnectionNames.contains(name);
		}
		return false;
	}

	private void loadValues() {
		if (fConnection != null) {
			fConnectionName.setText(fConnection.getName());
			fHostText.setText(fConnection.getAddress());
			fUserText.setText(fConnection.getUsername());
			fPortText.setText(Integer.toString(fConnection.getPort()));
			fTimeoutText.setText(Integer.toString(fConnection.getTimeout()));
			boolean isPwd = fConnection.isPasswordAuth();
			fPasswordButton.setSelection(isPwd);
			fPublicKeyButton.setSelection(!isPwd);
			if (isPwd) {
				fPasswordText.setText(fConnection.getPassword());
			} else {
				fPassphraseText.setText(fConnection.getPassphrase());
				fFileWidget.setLocationPath(fConnection.getKeyFile());
			}
			fProxyCommandText.setText(fConnection.getProxyCommand());

			fProxyConnectionWidget.setConnection(fConnection.getProxyConnection());
		} else {
			fConnectionName.setText(fInitialName);
			String host = fInitialAttributes.get(JSchConnectionAttributes.ADDRESS_ATTR);
			if (host != null) {
				fHostText.setText(host);
			}
			String username = fInitialAttributes.get(JSchConnectionAttributes.USERNAME_ATTR);
			if (username != null) {
				fUserText.setText(username);
			}
			String port = fInitialAttributes.get(JSchConnectionAttributes.PORT_ATTR);
			if (port != null) {
				fPortText.setText(port);
			}
			String timeout = fInitialAttributes.get(JSchConnectionAttributes.TIMEOUT_ATTR);
			if (timeout != null) {
				fTimeoutText.setText(timeout);
			}
			String isPwd = fInitialAttributes.get(JSchConnectionAttributes.IS_PASSWORD_ATTR);
			if (isPwd != null) {
				fPasswordButton.setSelection(Boolean.parseBoolean(isPwd));
			}
			String password = fInitialAttributes.get(JSchConnectionAttributes.PASSWORD_ATTR);
			if (password != null) {
				fPasswordText.setText(password);
			}
			String passphrase = fInitialAttributes.get(JSchConnectionAttributes.PASSPHRASE_ATTR);
			if (passphrase != null) {
				fPassphraseText.setText(passphrase);
			}
			String file = fInitialAttributes.get(JSchConnectionAttributes.KEYFILE_ATTR);
			if (file != null) {
				fFileWidget.setLocationPath(file);
			}
			fProxyConnectionWidget.setConnection(RemoteServices.getLocalServices().getConnectionManager().getConnection(
					IRemoteConnectionManager.LOCAL_CONNECTION_NAME));
		}
	}

	private void registerListeners() {
		fConnectionName.addModifyListener(fDataModifyListener);
		fHostText.addModifyListener(fDataModifyListener);
		fUserText.addModifyListener(fDataModifyListener);
		fFileWidget.addModifyListener(fDataModifyListener);
		fPasswordText.addModifyListener(fDataModifyListener);
		fPassphraseText.addModifyListener(fDataModifyListener);
		fPortText.addModifyListener(fDataModifyListener);
		fTimeoutText.addModifyListener(fDataModifyListener);
		fProxyCommandText.addModifyListener(fDataModifyListener);
		fProxyConnectionWidget.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				validateFields();
				getContainer().updateButtons();
			}
		});
	}

	public void setAddress(String address) {
		fInitialAttributes.put(JSchConnectionAttributes.ADDRESS_ATTR, address);
	}

	public void setAttributes(Map<String, String> attributes) {
		fInitialAttributes.putAll(attributes);
	}

	public void setConnection(JSchConnectionWorkingCopy connection) {
		fConnection = connection;
	}

	public void setConnectionName(String name) {
		fInitialName = name;
	}

	public void setInvalidConnectionNames(Set<String> names) {
		fInvalidConnectionNames = names;
	}

	@Override
	public void setPageComplete(boolean complete) {
		super.setPageComplete(complete);
		if (complete) {
			storeValues();
		}
	}

	public void setPort(int port) {
		fInitialAttributes.put(JSchConnectionAttributes.PORT_ATTR, Integer.toString(port));
	}

	private void setTextFieldWidthInChars(Text text, int chars) {
		text.setTextLimit(chars);
		Object data = text.getLayoutData();
		if (data instanceof GridData) {
			GC gc = new GC(text);
			FontMetrics fm = gc.getFontMetrics();
			int width = chars * fm.getAverageCharWidth();
			gc.dispose();
			((GridData) data).widthHint = width;
		}
	}

	public void setUsername(String username) {
		fInitialAttributes.put(JSchConnectionAttributes.USERNAME_ATTR, username);
	}

	private void storeValues() {
		if (fConnection == null) {
			try {
				JSchConnection conn = (JSchConnection) fConnectionManager.newConnection(fConnectionName.getText().trim());
				fConnection = (JSchConnectionWorkingCopy) conn.getWorkingCopy();
			} catch (RemoteConnectionException e) {
				Activator.log(e);
			}
		}
		if (fConnection != null) {
			if (!fConnection.getName().equals(fConnectionName.getText().trim())) {
				fConnection.setName(fConnectionName.getText().trim());
			}
			if (!fConnection.getAddress().equals(fHostText.getText().trim())) {
				fConnection.setAddress(fHostText.getText().trim());
			}
			if (!fConnection.getUsername().equals(fUserText.getText().trim())) {
				fConnection.setUsername(fUserText.getText().trim());
			}
			if (!fConnection.getPassword().equals(fPasswordText.getText().trim())) {
				fConnection.setPassword(fPasswordText.getText().trim());
			}
			if (!fConnection.getPassphrase().equals(fPassphraseText.getText().trim())) {
				fConnection.setPassphrase(fPassphraseText.getText().trim());
			}
			if (!fConnection.getKeyFile().equals(fFileWidget.getLocationPath())) {
				fConnection.setKeyFile(fFileWidget.getLocationPath());
			}
			if (fConnection.isPasswordAuth() != fPasswordButton.getSelection()) {
				fConnection.setIsPasswordAuth(fPasswordButton.getSelection());
			}
			int timeout = Integer.parseInt(fTimeoutText.getText().trim());
			if (fConnection.getTimeout() != timeout) {
				fConnection.setTimeout(timeout);
			}
			int port = Integer.parseInt(fPortText.getText().trim());
			if (fConnection.getPort() != port) {
				fConnection.setPort(port);
			}
			if (!fConnection.getProxyCommand().equals(fProxyCommandText.getText().trim())) {
				fConnection.setProxyCommand(fProxyCommandText.getText().trim());
			}
			IRemoteConnection proxyConnection = fProxyConnectionWidget.getConnection();
			String proxyConnectionName = ""; //$NON-NLS-1$
			if (proxyConnection != null && proxyConnection.getRemoteServices() != RemoteServices.getLocalServices()) {
				proxyConnectionName = proxyConnection.getName();
			}
			if (!fConnection.getProxyConnectionName().equals(proxyConnectionName)) {
				fConnection.setProxyConnectionName(proxyConnectionName);
			}
		}
	}

	private void updateEnablement() {
		boolean isPasswordAuth = fPasswordButton.getSelection();
		fPasswordText.setEnabled(isPasswordAuth);
		fPassphraseText.setEnabled(!isPasswordAuth);
		fFileWidget.setEnabled(!isPasswordAuth);
	}

	private String validateAdvanced() {
		try {
			Integer.parseInt(fPortText.getText().trim());
		} catch (NumberFormatException ne) {
			return Messages.JSchNewConnectionPage_Port_is_not_valid;
		}
		try {
			Integer.parseInt(fTimeoutText.getText().trim());
		} catch (NumberFormatException ne) {
			return Messages.JSchNewConnectionPage_Timeout_is_not_valid;
		}
		// if (fCipherCombo.getSelectionIndex() == -1) {
		// return "Invalid cipher type";
		// }
		return null;
	}

	private void validateFields() {
		String message = null;
		if (fConnectionName.getText().trim().length() == 0) {
			message = Messages.JSchNewConnectionPage_Please_enter_a_connection_name;
		} else if (isInvalidName(fConnectionName.getText().trim())) {
			message = Messages.JSchConnectionPage_A_connection_with_that_name_already_exists;
		} else if (fHostText.getText().trim().length() == 0) {
			message = Messages.JSchNewConnectionPage_Host_name_cannot_be_empty;
		} else if (fUserText.getText().trim().length() == 0) {
			message = Messages.JSchNewConnectionPage_User_name_cannot_be_empty;
		}
		if (message == null) {
			message = validatePasskey();
		}
		if (message == null && fProxyConnectionWidget.getConnection() == null) {
			message = Messages.JSchConnectionPage_selectProxyConnection;
		}
		if (message == null) {
			message = validateAdvanced();
		}

		setErrorMessage(message);
		setPageComplete(message == null);
	}

	private String validatePasskey() {
		if (!fPasswordButton.getSelection()) {
			if (fFileWidget.getLocationPath().trim().length() == 0) {
				return Messages.JSchNewConnectionPage_Private_key_path_cannot_be_empty;
			}
			File path = new File(fFileWidget.getLocationPath().trim());
			if (!path.exists()) {
				return Messages.JSchNewConnectionPage_Private_key_file_does_not_exist;
			}
			if (!path.isFile()) {
				return Messages.JSchNewConnectionPage_Private_key_file_is_invalid;
			}
			if (!path.canRead()) {
				return Messages.JSchNewConnectionPage_Private_key_file_cannot_be_read;
			}
		}
		return null;
	}
}
