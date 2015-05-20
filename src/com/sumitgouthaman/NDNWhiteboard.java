package com.sumitgouthaman;

import net.named_data.jndn.*;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.security.*;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.sync.ChronoSync2013;
import net.named_data.jndn.util.Blob;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by sumit on 5/17/15.
 */
public class NDNWhiteboard {

    Whiteboard whiteboardForm;
    Face m_face;
    boolean main_stop = false;
    ArrayList<String> dataHist = new ArrayList<String>();
    Map<String, Long> highestRequested = new HashMap<String, Long>();
    ChronoSync2013 sync = null;

    KeyChain keyChain;

    public NDNWhiteboard(Whiteboard whiteboardForm) {
        this.whiteboardForm = whiteboardForm;
        this.whiteboardForm.exitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stop();
            }
        });
        whiteboardForm.canvasPanel.setVisible(false);
        main_stop = false;
        System.out.println("Username: " + whiteboardForm.username);
        System.out.println("Whiteboard:  " + whiteboardForm.whiteboard);
        System.out.println("Prefix: " + whiteboardForm.prefix);
        initializeFace();
        performPing();
        loop();
//        stop();
    }


    public void setStatus(String s) {
        this.whiteboardForm.statusLabel.setText(s);
    }

    private void initializeFace() {
        m_face = new Face("localhost");
        setStatus("Initialized face");
        try {
            keyChain = buildTestKeyChain();
            keyChain.setFace(m_face);
            m_face.setCommandSigningInfo(keyChain, keyChain.getDefaultCertificateName());
        } catch (net.named_data.jndn.security.SecurityException e) {
            e.printStackTrace();
            setStatus("Error creating keychain");
            return;
        }
    }

    private void performPing() {
        setStatus("About to start Ping");
        Name ping_name = new Name("/ndn/edu/ucla/remap/ping");
        try {
            m_face.expressInterest(
                    new Interest(ping_name),
                    new OnData() {
                        @Override
                        public void onData(Interest interest, Data data) {
                            setStatus("Ping successful");
                            Blob ping_blob = data.getContent();
                            String ping_message = ping_blob.toString();
                            System.out.println("Ping message: " + ping_message);
                            registerPrefix();
                        }
                    }, new OnTimeout() {
                        @Override
                        public void onTimeout(Interest interest) {
                            setStatus("Ping failed");
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void registerPrefix() {
        setStatus("Starting register prefix");

        final String nameStr = whiteboardForm.prefix + "/" + whiteboardForm.whiteboard + "/" + whiteboardForm.username;
        System.out.println(nameStr);
        Name base_name = new Name(nameStr);
        try {

            m_face.registerPrefix(base_name, new OnInterestCallback() {
                @Override
                public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId, InterestFilter filter) {
                    Name interestName = interest.getName();
                    String lastComp = interestName.get(interestName.size() - 1).toEscapedString();
                    System.out.println("Interest received: " + lastComp);
                    int comp = Integer.parseInt(lastComp) - 1;

                    Data data = new Data();
                    data.setName(new Name(interestName));
                    Blob blob;
                    if (dataHist.size() > comp) {
                        blob = new Blob(dataHist.get(comp).getBytes());
                        data.setContent(blob);
                    } else {
                        return;
                    }
                    try {
                        face.putData(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, new OnRegisterFailed() {
                @Override
                public void onRegisterFailed(Name prefix) {
                    System.out.println("Register Prefix Task: Registration failed");
                }
            });

        } catch (IOException | SecurityException e) {
            e.printStackTrace();
            return;
        }
        registerChronosyncHelper();
    }

    private void registerChronosyncHelper() {
        setStatus("Starting chronosync");
        registerChronosync(1);
    }

    private void registerChronosync(int attempt) {
        try {
            System.out.println("ChronoSync Task: Attempt: " + attempt);

            sync = new ChronoSync2013(
                    new ChronoSync2013.OnReceivedSyncState() {
                        @Override
                        public void onReceivedSyncState(List syncStates, boolean isRecovery) {
                            for (Object syncStateOb : syncStates) {
                                ChronoSync2013.SyncState syncState = (ChronoSync2013.SyncState) syncStateOb;
                                String syncPrefix = syncState.getDataPrefix();
                                long syncSeq = syncState.getSequenceNo();
                                if (syncSeq == 0 || syncPrefix.contains(whiteboardForm.username)) {
                                    System.out.println("SYNC: prefix: " + syncPrefix + " seq: " + syncSeq + " ignored. (is Recovery: " + isRecovery + ")");
                                    continue;
                                }
                                if (highestRequested.keySet().contains(syncPrefix)) {
                                    long highestSeq = highestRequested.get(syncPrefix);
                                    if (syncSeq == highestSeq + 1) {
                                        highestRequested.put(syncPrefix, syncSeq);
                                    } else if (syncSeq <= highestSeq) {
                                        System.out.println("Avoiding starting new task for: " + syncPrefix + "/" + syncSeq);
                                        continue;
                                    } else if (syncSeq - highestSeq > 1) {
                                        System.out.println("Gaps in SYNC found. Sending Interest for missing pieces.");
                                        highestSeq++;
                                        while(highestSeq <= syncSeq) {
                                            fetchChangesTask(syncPrefix + "/" + highestSeq);
                                            highestSeq++;
                                        }
                                        highestRequested.put(syncPrefix, syncSeq);
                                    }
                                } else {
                                    highestRequested.put(syncPrefix, syncSeq);
                                }
                                String syncNameStr = syncPrefix + "/" + syncSeq;
                                System.out.println("SYNC: " + syncNameStr + " (is Recovery: " + isRecovery + ")");
                                fetchChangesTask(syncNameStr);
                            }

                        }
                    },
                    new ChronoSync2013.OnInitialized() {
                        @Override
                        public void onInitialized() {
                            System.out.println("ChronoSync onInitialized");
                            whiteboardForm.canvasPanel.setVisible(true);
                            setStatus("Chronosync initialized");
                        }
                    },
                    new Name(whiteboardForm.prefix + "/" + whiteboardForm.whiteboard + "/" + whiteboardForm.username), // App data prefix
                    new Name("/ndn/broadcast/whiteboard/" + whiteboardForm.whiteboard), // Broadcast prefix
                    0l,
                    m_face,
                    keyChain,
                    keyChain.getDefaultCertificateName(),
                    5000.0,
                    new OnRegisterFailed() {
                        @Override
                        public void onRegisterFailed(Name prefix) {
                            System.out.println("ChronoSync registration failed, Attempt: " + attempt);
                            System.out.println("Starting next attempt");
                            if (attempt != 3) {
                                registerChronosync(attempt + 1);
                            } else{
                                setStatus("Chronosync failed after 3 attempts");
                            }
                        }
                    }
            );
        } catch (IOException | SecurityException e) {
            e.printStackTrace();
        }
    }

    private void fetchChangesTask(String namePrefixStr) {
        System.out.println("In fetch task: " + namePrefixStr);

        try {
            m_face.expressInterest(new Name(namePrefixStr),
                    new OnData() {
                        @Override
                        public void
                        onData(Interest interest, Data data) {
                            String m_retVal = data.getContent().toString();
                            System.out.println("Got content: " + m_retVal);
                            whiteboardForm.callback(m_retVal);
                        }
                    },
                    new OnTimeout() {
                        @Override
                        public void onTimeout(Interest interest) {
                            System.out.println("Got Timeout " + namePrefixStr);
                            if (!main_stop) {
                                fetchChangesTask(namePrefixStr);
                            }
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loop() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!main_stop) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    try {
                        m_face.processEvents();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (EncodingException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    public void callback(String jsonData) {
        dataHist.add(jsonData);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (sync != null) {
                        while (sync.getSequenceNo() < dataHist.size() && sync.getSequenceNo() != -1) {
                            System.out.println("Seq is now: " + sync.getSequenceNo());
                            sync.publishNextSequenceNo();
                            System.out.println("Published next seq number. Seq is now: " + sync.getSequenceNo());
                        }
                    }
                } catch (IOException | SecurityException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        System.out.println("Stroke generated: " + jsonData);
    }

    private void stop() {
        main_stop = true;
        if (m_face != null) {
            m_face.shutdown();
            System.out.println("Face shutdown");
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        stop();
    }

    /**
     * Setup an in-memory KeyChain with a default identity.
     *
     * @return keyChain object
     * @throws net.named_data.jndn.security.SecurityException
     */
    public static KeyChain buildTestKeyChain() throws net.named_data.jndn.security.SecurityException {
        MemoryIdentityStorage identityStorage = new MemoryIdentityStorage();
        MemoryPrivateKeyStorage privateKeyStorage = new MemoryPrivateKeyStorage();
        IdentityManager identityManager = new IdentityManager(identityStorage, privateKeyStorage);
        KeyChain keyChain = new KeyChain(identityManager);
        try {
            keyChain.getDefaultCertificateName();
        } catch (net.named_data.jndn.security.SecurityException e) {
            keyChain.createIdentity(new Name("/test/identity"));
            keyChain.getIdentityManager().setDefaultIdentity(new Name("/test/identity"));
        }
        return keyChain;
    }
}
