/*
 *  Kontalk Java client
 *  Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.view;

import com.alee.laf.label.WebLabel;
import com.alee.laf.list.UnselectableListModel;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.text.WebEditorPane;
import com.alee.laf.text.WebTextPane;
import com.alee.managers.tooltip.TooltipManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractCellEditor;
import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellEditor;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BoxView;
import javax.swing.text.ComponentView;
import javax.swing.text.Element;
import javax.swing.text.IconView;
import javax.swing.text.LabelView;
import javax.swing.text.ParagraphView;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.ViewFactory;
import org.jxmpp.util.XmppStringUtils;
import org.kontalk.crypto.Coder;
import org.kontalk.model.InMessage;
import org.kontalk.model.KonMessage;
import org.kontalk.model.Chat;
import org.kontalk.model.MessageContent;
import org.kontalk.model.Contact;
import org.kontalk.model.MessageContent.Attachment;
import org.kontalk.model.Transmission;
import org.kontalk.util.Tr;
import org.kontalk.view.ChatView.Background;
import org.kontalk.view.ComponentUtils.AttachmentPanel;


/**
 * View all messages of one chat in a left/right MIM style list.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
final class MessageList extends Table<MessageList.MessageItem, KonMessage> {
    private static final Logger LOGGER = Logger.getLogger(MessageList.class.getName());

    private static final Icon PENDING_ICON = Utils.getIcon("ic_msg_pending.png");;
    private static final Icon SENT_ICON = Utils.getIcon("ic_msg_sent.png");
    private static final Icon DELIVERED_ICON = Utils.getIcon("ic_msg_delivered.png");
    private static final Icon ERROR_ICON = Utils.getIcon("ic_msg_error.png");
    private static final Icon WARNING_ICON = Utils.getIcon("ic_msg_warning.png");
    private static final Icon CRYPT_ICON = Utils.getIcon("ic_msg_crypt.png");
    private static final Icon UNENCRYPT_ICON = Utils.getIcon("ic_msg_unencrypt.png");

    private final ChatView mChatView;
    private final Chat mChat;
    private Optional<Background> mBackground = Optional.empty();

    MessageList(View view, ChatView chatView, Chat chat) {
        super(view, false);
        mChatView = chatView;
        mChat = chat;

        // use custom editor (for mouse events)
        this.setDefaultEditor(Table.TableItem.class, new TableEditor());

        //this.setEditable(false);
        //this.setAutoscrolls(true);
        this.setOpaque(false);

        // hide grid
        this.setShowGrid(false);

        // disable selection
        this.setSelectionModel(new UnselectableListModel());

        // actions triggered by mouse events
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                check(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                check(e);
            }
            private void check(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    MessageList.this.showPopupMenu(e);
                }
            }
        });

        this.setBackground(mChat.getViewSettings());

        this.setVisible(false);
        this.updateOnEDT(null);
        this.setVisible(true);
    }

    Chat getChat() {
        return mChat;
    }

    Optional<Background> getBG() {
        return mBackground;
    }

    @Override
    protected void updateOnEDT(Object arg) {
        if (arg instanceof Set ||
                arg instanceof String ||
                arg instanceof Boolean ||
                arg instanceof Chat.KonChatState) {
            // contacts, subject, read status or chat state changed, nothing
            // to do here
            return;
        }

        if (arg instanceof Chat.ViewSettings) {
            this.setBackground((Chat.ViewSettings) arg);
            if (mChatView.getCurrentChat().orElse(null) == mChat) {
                //mChatView.mScrollPane.getViewport().repaint();
                mChatView.repaint();
            }
            return;
        }

        if (arg instanceof KonMessage) {
            this.insertMessage((KonMessage) arg);
        } else {
            // check for new messages to add
            if (this.getModel().getRowCount() < mChat.getMessages().getAll().size())
                this.insertMessages();
        }

        if (mChatView.getCurrentChat().orElse(null) == mChat) {
            mChat.setRead();
        }
    }

    private void insertMessages() {
        Set<MessageItem> newItems = new HashSet<>();
        for (KonMessage message: mChat.getMessages().getAll()) {
            if (!this.containsValue(message)) {
                newItems.add(new MessageItem(message));
                // trigger scrolling
                mChatView.setScrolling();
            }
        }
        this.sync(mChat.getMessages().getAll(), newItems);
    }

    private void insertMessage(KonMessage message) {
        Set<MessageItem> newItems = new HashSet<>();
        newItems.add(new MessageItem(message));
        this.sync(mChat.getMessages().getAll(), newItems);
        // trigger scrolling
        mChatView.setScrolling();
    }

    private void showPopupMenu(MouseEvent e) {
        int row = this.rowAtPoint(e.getPoint());
        if (row < 0)
            return;

        MessageItem messageView = this.getDisplayedItemAt(row);
        WebPopupMenu popupMenu = messageView.getPopupMenu();
        popupMenu.show(this, e.getX(), e.getY());
    }

    private void setBackground(Chat.ViewSettings s) {
        // simply overwrite
        mBackground = mChatView.createBG(s);
    }

    /**
     * View for one message.
     * The content is added to a panel inside this panel. For performance
     * reasons the content is created when the item is rendered in the table
     */
    final class MessageItem extends Table<MessageItem, KonMessage>.TableItem {

        private WebLabel mFromLabel = null;
        private WebPanel mContentPanel;
        private WebTextPane mTextPane;
        private WebPanel mStatusPanel;
        private WebLabel mStatusIconLabel;
        private AttachmentPanel mAttPanel = null;
        private int mPreferredTextWidth;
        private boolean mCreated = false;

        MessageItem(KonMessage message) {
            super(message);

            this.setOpaque(false);
            this.setMargin(2);
            //this.setBorder(new EmptyBorder(10, 10, 10, 10));
        }

        private void createContent() {
            if (mCreated)
                return;
            mCreated = true;

            WebPanel messagePanel = new WebPanel(true);
            messagePanel.setWebColoredBackground(false);
            messagePanel.setMargin(2);
            if (mValue.isInMessage())
                messagePanel.setBackground(Color.WHITE);
            else
                messagePanel.setBackground(View.LIGHT_BLUE);

            // from label
            if (mValue.isInMessage()) {
                mFromLabel = new WebLabel();
                mFromLabel.setFontSize(12);
                mFromLabel.setForeground(Color.BLUE);
                mFromLabel.setItalicFont();
                messagePanel.add(mFromLabel, BorderLayout.NORTH);
            }

            mContentPanel = new WebPanel();
            mContentPanel.setOpaque(false);
            mContentPanel.setMargin(View.MARGIN_SMALL);
            // text area
            mTextPane = new WebTextPane();
            mTextPane.setEditable(false);
            mTextPane.setOpaque(false);
            //mTextPane.setFontSize(12);
            // sets default font
            mTextPane.putClientProperty(WebEditorPane.HONOR_DISPLAY_PROPERTIES, true);
            //for detecting clicks
            mTextPane.addMouseListener(LinkUtils.CLICK_LISTENER);
            //for detecting motion
            mTextPane.addMouseMotionListener(LinkUtils.MOTION_LISTENER);
            // fix word wrap for long words
            mTextPane.setEditorKit(FIX_WRAP_KIT);
            mContentPanel.add(mTextPane, BorderLayout.CENTER);
            messagePanel.add(mContentPanel, BorderLayout.CENTER);

            mStatusPanel = new WebPanel();
            mStatusPanel.setOpaque(false);
            TooltipManager.addTooltip(mStatusPanel, "???");
            mStatusPanel.setLayout(new FlowLayout());
            // icons
            mStatusIconLabel = new WebLabel();

            this.updateOnEDT(null);

            // save the width that is requied to show the text in one line;
            // before line wrap and only once!
            mPreferredTextWidth = mTextPane.getPreferredSize().width;

            mStatusPanel.add(mStatusIconLabel);
            WebLabel encryptIconLabel = new WebLabel();
            if (mValue.getCoderStatus().isSecure()) {
                encryptIconLabel.setIcon(CRYPT_ICON);
            } else {
                encryptIconLabel.setIcon(UNENCRYPT_ICON);
            }
            mStatusPanel.add(encryptIconLabel);
            // date label
            WebLabel dateLabel = new WebLabel(Utils.SHORT_DATE_FORMAT.format(mValue.getDate()));
            dateLabel.setForeground(Color.GRAY);
            dateLabel.setFontSize(11);
            mStatusPanel.add(dateLabel);

            WebPanel southPanel = new WebPanel();
            southPanel.setOpaque(false);
            southPanel.add(mStatusPanel, BorderLayout.EAST);
            messagePanel.add(southPanel, BorderLayout.SOUTH);

            if (mValue.isInMessage()) {
                this.add(messagePanel, BorderLayout.WEST);
            } else {
                this.add(messagePanel, BorderLayout.EAST);
            }

            for (Transmission t: mValue.getTransmissions()) {
                t.getContact().addObserver(this);
            }
        }

        @Override
        protected void render(int listWidth, boolean isSelected) {
            this.createContent();

            // note: on the very first call the list width is zero
            int maxWidth = (int)(listWidth * 0.8);
            int width = Math.min(mPreferredTextWidth, maxWidth);
            // height is reset later
            mTextPane.setSize(width, -1);
            // textArea does not need this but textPane does, and editorPane
            // is again totally different; I love Swing
            mTextPane.setPreferredSize(new Dimension(width, mTextPane.getMinimumSize().height));
        }

        @Override
        protected void updateOnEDT(Object arg) {
            if (!mCreated)
                return;

            if ((arg == null || arg instanceof Contact) && mFromLabel != null &&
                    mValue instanceof InMessage)
                mFromLabel.setText(" "+getFromString((InMessage) mValue));

            if (arg == null || arg instanceof String)
                this.updateText();

            if (arg == null || arg instanceof KonMessage.Status)
                this.updateStatus();

            if (arg == null || arg instanceof MessageContent.Attachment ||
                    arg instanceof MessageContent.Preview)
                this.updateAttachment();

            // changes are not instantly painted
            // TODO height problem for new messages again
            MessageList.this.repaint();
        }

        // text in text area, before/after encryption
        private void updateText() {
            boolean encrypted = mValue.getCoderStatus().isEncrypted();
            String text = encrypted ?
                    Tr.tr("[encrypted]") :
                    // removing whitespace (Pidgin adds weird tab characters)
                    mValue.getContent().getText().trim();
            // TODO this could look A BIT nicer
            if (mValue.getContent().getGroupCommand().isPresent())
                text += " "+ mValue.getContent().getGroupCommand().get();
            mTextPane.setFontStyle(false, encrypted);
            //mTextPane.setText(text);
            LinkUtils.linkify(mTextPane.getStyledDocument(), text);
            // hide area if there is no text
            mTextPane.setVisible(!text.isEmpty());
        }

        private void updateStatus() {
            boolean isOut = !mValue.isInMessage();

            Date deliveredDate = null;
            Optional<Transmission> optTransmission = mValue.getSingleTransmission();
            if (optTransmission.isPresent())
                deliveredDate = optTransmission.get().getReceivedDate().orElse(null);

            // status icon
            if (isOut) {
                if (deliveredDate != null) {
                    mStatusIconLabel.setIcon(DELIVERED_ICON);
                } else {
                    switch (mValue.getStatus()) {
                        case PENDING :
                            mStatusIconLabel.setIcon(PENDING_ICON);
                            break;
                        case SENT :
                            mStatusIconLabel.setIcon(SENT_ICON);
                            break;
                        case RECEIVED:
                            // legacy
                            mStatusIconLabel.setIcon(DELIVERED_ICON);
                            break;
                        case ERROR:
                            mStatusIconLabel.setIcon(ERROR_ICON);
                            break;
                        default:
                            LOGGER.warning("unknown message receipt status!?");
                    }
                }
            } else { // IN message
                if (!mValue.getCoderStatus().getErrors().isEmpty()) {
                    mStatusIconLabel.setIcon(WARNING_ICON);
                }
            }

            // tooltip
            String html = "<html><body>" + /*"<h3>Header</h3>"+*/ "<br>";

            if (isOut) {
                String secStat = null;
                Date statusDate;
                if (deliveredDate != null) {
                    secStat = Tr.tr("Delivered:");
                    statusDate = deliveredDate;
                } else {
                    statusDate = mValue.getServerDate().orElse(null);
                    switch (mValue.getStatus()) {
                        case PENDING:
                            break;
                        case SENT:
                            secStat = Tr.tr("Sent:");
                            break;
                        // legacy
                        case RECEIVED:
                            secStat = Tr.tr("Delivered:");
                            break;
                        case ERROR:
                            secStat = Tr.tr("Error report:");
                            break;
                        default:
                            LOGGER.warning("unexpected msg status: "+mValue.getStatus());
                    }
                }

                String status = statusDate != null ?
                        Utils.MID_DATE_FORMAT.format(statusDate) :
                        null;

                String create = Utils.MID_DATE_FORMAT.format(mValue.getDate());
                if (!create.equals(status))
                    html += Tr.tr("Created:")+ " " + create + "<br>";

                if (status != null && secStat != null)
                    html += secStat + " " + status + "<br>";
            } else { // IN message
                Date receivedDate = mValue.getDate();
                String rec = Utils.MID_DATE_FORMAT.format(receivedDate);
                Optional<Date> sentDate = mValue.getServerDate();
                if (sentDate.isPresent()) {
                    String sent = Utils.MID_DATE_FORMAT.format(sentDate.get());
                    if (!sent.equals(rec))
                        html += Tr.tr("Sent:")+ " " + sent + "<br>";
                }
                html += Tr.tr("Received:")+ " " + rec + "<br>";
            }

            Coder.Encryption enc = mValue.getCoderStatus().getEncryption();
            Coder.Signing sign = mValue.getCoderStatus().getSigning();
            String sec = null;
            // usual states
            if (enc == Coder.Encryption.NOT && sign == Coder.Signing.NOT)
                sec = Tr.tr("Not encrypted");
            else if (enc == Coder.Encryption.DECRYPTED &&
                    ((isOut && sign == Coder.Signing.SIGNED) ||
                    (!isOut && sign == Coder.Signing.VERIFIED))) {
                        sec = Tr.tr("Secure");
            }
            if (sec == null) {
                // unusual states
                String encryption = Tr.tr("Unknown");
                switch (enc) {
                    case NOT: encryption = Tr.tr("Not encrypted"); break;
                    case ENCRYPTED: encryption = Tr.tr("Encrypted"); break;
                    case DECRYPTED: encryption = Tr.tr("Decrypted"); break;
                }
                String verification = Tr.tr("Unknown");
                switch (sign) {
                    case NOT: verification = Tr.tr("Not signed"); break;
                    case SIGNED: verification = Tr.tr("Signed"); break;
                    case VERIFIED: verification = Tr.tr("Verified"); break;
                }
                sec = encryption + " / " + verification;
            }
            html += Tr.tr("Encryption")+": " + sec + "<br>";

            String errors = "";
            for (Coder.Error error: mValue.getCoderStatus().getErrors()) {
                errors += error.toString() + " <br> ";
            }
            if (!errors.isEmpty())
                html += Tr.tr("Security errors")+": " + errors;

            String serverErrText = mValue.getServerError().text;
            if (!serverErrText.isEmpty())
                html += Tr.tr("Server error")+": " + serverErrText + " <br> ";

            // TODO temporary catching for tracing bug
            try {
                TooltipManager.setTooltip(mStatusPanel, html);
            } catch (NullPointerException ex) {
                LOGGER.log(Level.WARNING, "cant set tooltip", ex);
                LOGGER.warning("statusPanel="+mStatusPanel+",html="+html);
                LOGGER.warning("edt: "+SwingUtilities.isEventDispatchThread());
            }
        }

        // attachment / image, note: loading many images is very slow
        private void updateAttachment() {
            Optional<Attachment> optAttachment = mValue.getContent().getAttachment();
            if (!optAttachment.isPresent())
                return;
            Attachment att = optAttachment.get();

            if (mAttPanel == null) {
                mAttPanel = new AttachmentPanel();
                mContentPanel.add(mAttPanel, BorderLayout.SOUTH);
            }

            // image thumbnail preview
            Optional<Path> optImagePath = mView.getControl().getImagePath(mValue);
            String imagePath = optImagePath.isPresent() ? optImagePath.get().toString() : "";
            mAttPanel.setImage(imagePath);

            // link to the file
            Path linkPath = mView.getControl().getFilePath(att);
            if (!linkPath.toString().isEmpty()) {
                mAttPanel.setLink(imagePath.isEmpty() ?
                        linkPath.getFileName().toString() :
                        "",
                        linkPath);
            } else {
                // status text
                String statusText = Tr.tr("loading...");
                switch (att.getDownloadProgress()) {
                    case -1: statusText = Tr.tr("stalled"); break;
                    case 0:
                    case -2: statusText = Tr.tr("downloading..."); break;
                    case -3: statusText = Tr.tr("download failed"); break;
                }
                mAttPanel.setStatus(statusText);
            }
        }

        private WebPopupMenu getPopupMenu() {
            WebPopupMenu popupMenu = new WebPopupMenu();
            final KonMessage m = MessageItem.this.mValue;
            if (m instanceof InMessage) {
                if (m.getCoderStatus().isEncrypted()) {
                    WebMenuItem decryptMenuItem = new WebMenuItem(Tr.tr("Decrypt"));
                    decryptMenuItem.setToolTipText(Tr.tr("Retry decrypting message"));
                    decryptMenuItem.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent event) {
                            mView.getControl().decryptAgain((InMessage) m);
                        }
                    });
                    popupMenu.add(decryptMenuItem);
                }
                Optional<Attachment> optAtt = m.getContent().getAttachment();
                if (optAtt.isPresent() &&
                        optAtt.get().getFile().toString().isEmpty()) {
                    WebMenuItem attMenuItem = new WebMenuItem(Tr.tr("Load"));
                    attMenuItem.setToolTipText(Tr.tr("Retry downloading attachment"));
                    attMenuItem.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent event) {
                            mView.getControl().downloadAgain((InMessage) m);
                        }
                    });
                    popupMenu.add(attMenuItem);
                }
            }

            WebMenuItem cItem = Utils.createCopyMenuItem(
                    this.toCopyString(),
                    Tr.tr("Copy message content"));
            popupMenu.add(cItem);
            return popupMenu;
        }

        private String toCopyString() {
            String date = Utils.LONG_DATE_FORMAT.format(mValue.getDate());
            String from = mValue instanceof InMessage ?
                    getFromString((InMessage) mValue) :
                    Tr.tr("me");
            return date + " - " + from + " : " + mValue.getContent().getText();
        }

        @Override
        protected boolean contains(String search) {
            if (mValue.getContent().getText().toLowerCase().contains(search))
                return true;
            for (Transmission t: mValue.getTransmissions()) {
                if (t.getContact().getName().toLowerCase().contains(search) ||
                        t.getContact().getJID().toLowerCase().contains(search))
                    return true;
            }

            return false;
        }

        @Override
        protected void onRemove() {
            for (Transmission t: mValue.getTransmissions()) {
                t.getContact().deleteObserver(this);
            }
        }

        @Override
        public int compareTo(TableItem o) {
            int idComp = Integer.compare(mValue.getID(), o.mValue.getID());
            int dateComp = mValue.getDate().compareTo(mValue.getDate());
            return (idComp == 0 || dateComp == 0) ? idComp : dateComp;
        }
    }

    // needed for correct mouse behaviour for components in items
    // (and breaks selection behaviour somehow)
    private class TableEditor extends AbstractCellEditor implements TableCellEditor {
        private Table<?, ?>.TableItem mValue;
        @Override
        public Component getTableCellEditorComponent(JTable table,
                Object value,
                boolean isSelected,
                int row,
                int column) {
            mValue = (Table.TableItem) value;
            return mValue;
        }
        @Override
        public Object getCellEditorValue() {
            return mValue;
        }
    }

    private static String getFromString(InMessage message) {
        String from;
        if (!message.getContact().getName().isEmpty()) {
            from = message.getContact().getName();
        } else {
            from = XmppStringUtils.parseBareJid(message.getJID());
            if (from.length() > 40)
                from = from.substring(0, 8) + "...";
        }
        return from;
    }

    private static final WrapEditorKit FIX_WRAP_KIT = new WrapEditorKit();

    /**
     * Fix for the infamous "Wrap long words" problem in Java 7+.
     * Source: https://stackoverflow.com/a/13375811
     */
    private static class WrapEditorKit extends StyledEditorKit {
        ViewFactory defaultFactory = new WrapColumnFactory();
        @Override
        public ViewFactory getViewFactory() {
            return defaultFactory;
        }

        private static class WrapColumnFactory implements ViewFactory {
            @Override
            public javax.swing.text.View create(Element elem) {
                String kind = elem.getName();
                if (kind != null) {
                    switch (kind) {
                        case AbstractDocument.ContentElementName:
                            return new WrapLabelView(elem);
                        case AbstractDocument.ParagraphElementName:
                            return new ParagraphView(elem);
                        case AbstractDocument.SectionElementName:
                            return new BoxView(elem, javax.swing.text.View.Y_AXIS);
                        case StyleConstants.ComponentElementName:
                            return new ComponentView(elem);
                        case StyleConstants.IconElementName:
                            return new IconView(elem);
                    }
                }
                // default to text display
                return new LabelView(elem);
            }
        }

        private static class WrapLabelView extends LabelView {
            public WrapLabelView(Element elem) {
                super(elem);
            }
            @Override
            public float getMinimumSpan(int axis) {
                switch (axis) {
                    case javax.swing.text.View.X_AXIS:
                        return 0;
                    case javax.swing.text.View.Y_AXIS:
                        return super.getMinimumSpan(axis);
                    default:
                        throw new IllegalArgumentException("Invalid axis: " + axis);
                }
            }
        }
    }
}