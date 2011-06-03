package net.refractions.udig.catalog.internal.wfs;

import java.io.IOException;

import net.refractions.udig.catalog.wfs.internal.Messages;
import net.refractions.udig.project.EditManagerEvent;
import net.refractions.udig.project.IEditManagerListener;
import net.refractions.udig.project.interceptor.MapInterceptor;
import net.refractions.udig.project.internal.EditManager;
import net.refractions.udig.project.internal.Map;

import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.geotools.data.Transaction;

/**
 * Listens to transaction commit requests and shows a pop up dialog allowing to set a commit message
 * so that it is transferred by the WFS datastore as the transaction handle.
 * 
 * @author groldan
 * @since 1.2.0
 */
public class TransactionCommitMessageMapInterceptor implements MapInterceptor {

    public TransactionCommitMessageMapInterceptor() {
        //
    }

    /**
     * @see net.refractions.udig.project.interceptor.MapInterceptor#run(net.refractions.udig.project.internal.Map)
     */
    public void run( final Map map ) {
        final IEditManagerListener listener = new IEditManagerListener(){

            /**
             * @see net.refractions.udig.project.IEditManagerListener#changed(net.refractions.udig.project.EditManagerEvent)
             */
            public void changed( final EditManagerEvent event ) {
                final int type = event.getType();
                if (EditManagerEvent.PRE_COMMIT != type) {
                    return;
                }

                final EditManager editManager = (EditManager) event.getSource();
                final Transaction transaction = editManager.getTransaction();
                final Display defaultDisplay = Display.getDefault();

                defaultDisplay.syncExec(new Runnable(){

                    public void run() {
                        final Shell activeShell = defaultDisplay.getActiveShell();
                        final String title = Messages.TransactionCommitMessageMapInterceptor_CommitMessage;
                        final String request = Messages.TransactionCommitMessageMapInterceptor_CommitMessageRequest;
                        final String defaultCommitMessage = Messages.TransactionCommitMessageMapInterceptor_DefaultCommitMessage;
                        final InputDialog dialog = new InputDialog(activeShell, title, request,
                                defaultCommitMessage, null);
                        dialog.open();

                        final String input = dialog.getValue();
                        try {
                            transaction.putProperty("handle", input); //$NON-NLS-1$
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });

            }
        };
        map.getEditManagerInternal().addListener(listener);
    }

}
