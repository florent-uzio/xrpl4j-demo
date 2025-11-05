package com.xrpl4j.xrpl4j_demo;

import org.apache.commons.codec.binary.Hex;
import org.xrpl.xrpl4j.client.XrplClient;
import org.xrpl.xrpl4j.crypto.keys.Base58EncodedSecret;
import org.xrpl.xrpl4j.crypto.keys.KeyPair;
import org.xrpl.xrpl4j.crypto.keys.PrivateKey;
import org.xrpl.xrpl4j.crypto.keys.Seed;
import org.xrpl.xrpl4j.crypto.signing.SignatureService;
import org.xrpl.xrpl4j.crypto.signing.SingleSignedTransaction;
import org.xrpl.xrpl4j.crypto.signing.bc.BcSignatureService;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoRequestParams;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoResult;
import org.xrpl.xrpl4j.model.client.transactions.SubmitResult;
import org.xrpl.xrpl4j.model.client.transactions.TransactionRequestParams;
import org.xrpl.xrpl4j.model.flags.MpTokenIssuanceCreateFlags;
import org.xrpl.xrpl4j.model.transactions.Address;
import org.xrpl.xrpl4j.model.transactions.AssetScale;
import org.xrpl.xrpl4j.model.transactions.MpTokenAuthorize;
import org.xrpl.xrpl4j.model.transactions.MpTokenIssuanceCreate;
import org.xrpl.xrpl4j.model.transactions.MpTokenIssuanceId;
import org.xrpl.xrpl4j.model.transactions.MpTokenMetadata;
import org.xrpl.xrpl4j.model.transactions.MpTokenNumericAmount;
import org.xrpl.xrpl4j.model.transactions.MptCurrencyAmount;
import org.xrpl.xrpl4j.model.transactions.Payment;
import org.xrpl.xrpl4j.model.transactions.TransferFee;
import org.xrpl.xrpl4j.model.transactions.XrpCurrencyAmount;

import java.util.concurrent.TimeUnit;

import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;

import okhttp3.HttpUrl;

/**
 * MPT (Multi-Purpose Token) Service Demo
 * 
 * This class demonstrates the complete lifecycle of an MPT token on the XRPL:
 * 1. Creating an MPT issuance (issuing a new token)
 * 2. Authorizing a recipient to receive the token
 * 3. Transferring tokens from issuer to recipient
 * 
 * The demo uses XRPL Testnet for safe testing.
 */
public class MptService {

    // ============================================================================
    // Configuration Constants
    // ============================================================================

    /** XRPL Testnet endpoint URL */
    private static final String XRPL_TESTNET_URL = "https://s.altnet.rippletest.net:51234/";

    /** Testnet explorer URL for viewing transactions */
    private static final String TESTNET_EXPLORER_URL = "https://testnet.xrpl.org/transactions/";

    /**
     * Base58-encoded secret key for the issuer account
     * (rsNnw5i5tbgyjedDRNSmBk2Jw7PHHziPJ5)
     */
    private static final String ISSUER_SECRET = "sEd7HFg4UKpa4UA6CJAxNLZcMF4kYbE";

    /**
     * Base58-encoded secret key for the recipient/holder account
     * (rDirbKUBmzJnqNzFEo9KaqLzpz62B4PmJY)
     */
    private static final String RECIPIENT_SECRET = "sEdTKevpT15jdZBRgLcT3Ye8rvkrY8P";

    /** MPT token metadata (converted to hex string) */
    private static final String TOKEN_METADATA = "test";

    /** Transfer fee in basis points (100 = 1%) */
    private static final int TRANSFER_FEE_BASIS_POINTS = 100;

    /** Asset scale (decimal places) - 6 means tokens have 6 decimal places */
    private static final int ASSET_SCALE = 0;

    /** Maximum amount of tokens that can be issued */
    private static final long MAXIMUM_TOKEN_AMOUNT = 1000L;

    /** Amount of tokens to transfer, 1 token based on the asset scale */
    private static final String TRANSFER_AMOUNT = "1";

    /** Transaction fee in drops (12 drops is minimum) */
    private static final long TRANSACTION_FEE_DROPS = 12L;

    /** Wait time after transaction submission to allow for processing */
    private static final int TRANSACTION_WAIT_TIME_SECONDS = 5;

    // ============================================================================
    // Main Method
    // ============================================================================

    /**
     * Main entry point for the MPT demo.
     * Executes the complete MPT lifecycle: creation, authorization, and transfer.
     * 
     * @param args Command line arguments (not used)
     * @throws Exception if any step in the process fails
     */
    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("XRPL MPT (Multi-Purpose Token) Demo");
        System.out.println("========================================\n");

        // Initialize connection and services
        XrplClient xrplClient = initializeXrplClient();
        SignatureService<PrivateKey> signatureService = new BcSignatureService();

        // Initialize issuer and recipient accounts
        AccountDetails issuer = initializeAccount(ISSUER_SECRET, "Issuer");
        AccountDetails recipient = initializeAccount(RECIPIENT_SECRET, "Recipient");

        // Step 1: Create MPT Issuance
        MpTokenIssuanceId mptIssuanceId = createMptIssuance(xrplClient, signatureService, issuer);

        // Step 2: Authorize recipient to receive MPT tokens
        authorizeMptHolder(xrplClient, signatureService, recipient, mptIssuanceId);

        // Step 3: Transfer MPT tokens from issuer to recipient
        transferMptTokens(xrplClient, signatureService, issuer, recipient, mptIssuanceId);

        System.out.println("\n========================================");
        System.out.println("Demo completed successfully!");
        System.out.println("========================================");
    }

    // ============================================================================
    // Initialization Methods
    // ============================================================================

    /**
     * Initializes the XRPL client connection to the testnet.
     * 
     * @return Configured XrplClient instance
     */
    private static XrplClient initializeXrplClient() {
        System.out.println("Connecting to XRPL Testnet...");
        return new XrplClient(HttpUrl.get(XRPL_TESTNET_URL));
    }

    /**
     * Initializes an account from a secret key.
     * Derives the keypair and address from the seed.
     * 
     * @param secretKey   Base58-encoded secret key
     * @param accountName Name of the account (for logging)
     * @return AccountDetails containing keypair, private key, and address
     */
    private static AccountDetails initializeAccount(String secretKey, String accountName) {
        System.out.println("Initializing " + accountName + " account...");

        Base58EncodedSecret secret = Base58EncodedSecret.of(secretKey);
        Seed seed = Seed.fromBase58EncodedSecret(secret);
        KeyPair keyPair = seed.deriveKeyPair();
        Address address = keyPair.publicKey().deriveAddress();

        System.out.println(accountName + " address: " + address.value());

        return new AccountDetails(keyPair, keyPair.privateKey(), address);
    }

    // ============================================================================
    // MPT Issuance Creation
    // ============================================================================

    /**
     * Creates a new MPT (Multi-Purpose Token) issuance on the XRPL.
     * This is the first step in the MPT lifecycle - creating the token itself.
     * 
     * @param xrplClient       The XRPL client for network interaction
     * @param signatureService Service for signing transactions
     * @param issuer           The account that will issue the token
     * @return The MpTokenIssuanceId that uniquely identifies this token
     * @throws Exception if issuance creation fails
     */
    private static MpTokenIssuanceId createMptIssuance(
            XrplClient xrplClient,
            SignatureService<PrivateKey> signatureService,
            AccountDetails issuer) throws Exception {

        System.out.println("\n--- Step 1: Creating MPT Issuance ---");

        // Get current account sequence number (required for transaction)
        AccountInfoResult accountInfo = getAccountInfo(xrplClient, issuer.address);

        // Configure MPT capabilities (flags)
        // These determine what operations can be performed with the token
        MpTokenIssuanceCreateFlags flags = MpTokenIssuanceCreateFlags.builder()
                .tfMptCanLock(true) // Token can be locked
                .tfMptCanEscrow(true) // Token can be put in escrow
                .tfMptCanTrade(true) // Token can be traded
                .tfMptCanTransfer(true) // Token can be transferred
                .tfMptCanClawback(true) // Token can be clawed back by issuer
                .build();

        // Create token metadata (must be hex-encoded)
        String hexMetadata = Hex.encodeHexString(TOKEN_METADATA.getBytes("UTF-8"));
        MpTokenMetadata mpTokenMetadata = MpTokenMetadata.of(hexMetadata);

        // Build the MPT Issuance transaction
        MpTokenIssuanceCreate mpTokenIssuanceCreate = MpTokenIssuanceCreate.builder()
                .account(issuer.address) // Issuer's address
                .transferFee(TransferFee.of(UnsignedInteger.valueOf(TRANSFER_FEE_BASIS_POINTS))) // 1% transfer fee
                .assetScale(AssetScale.of(UnsignedInteger.valueOf(ASSET_SCALE))) // 6 decimal places
                .mpTokenMetadata(mpTokenMetadata) // Token metadata
                .maximumAmount(MpTokenNumericAmount.of(UnsignedLong.valueOf(MAXIMUM_TOKEN_AMOUNT))) // Max supply
                .fee(XrpCurrencyAmount.ofDrops(TRANSACTION_FEE_DROPS)) // Transaction fee
                .sequence(accountInfo.accountData().sequence()) // Account sequence number
                .signingPublicKey(issuer.keyPair.publicKey()) // Public key for verification
                .flags(flags) // Token capabilities
                .build();

        // Sign and submit the transaction
        SingleSignedTransaction<MpTokenIssuanceCreate> signedTx = signatureService.sign(issuer.privateKey,
                mpTokenIssuanceCreate);

        SubmitResult<MpTokenIssuanceCreate> result = xrplClient.submit(signedTx);

        // Display transaction results
        printTransactionResult("MPT Issuance", result);

        // Wait for transaction to be processed before retrieving issuance ID
        System.out.println("Waiting " + TRANSACTION_WAIT_TIME_SECONDS + " seconds for transaction to be processed...");
        TimeUnit.SECONDS.sleep(TRANSACTION_WAIT_TIME_SECONDS);

        // Retrieve the MPT Issuance ID from the transaction metadata
        // This ID is required for all subsequent operations with this token
        MpTokenIssuanceId mptIssuanceId = retrieveMptIssuanceId(xrplClient, signedTx);

        System.out.println("MpTokenIssuanceId: " + mptIssuanceId.value());
        System.out.println("--------------------------------");

        return mptIssuanceId;
    }

    /**
     * Retrieves the MPT Issuance ID from a submitted transaction.
     * The issuance ID is contained in the transaction metadata and is required
     * for all future operations with this token.
     * 
     * @param xrplClient The XRPL client
     * @param signedTx   The signed transaction that created the issuance
     * @return The MpTokenIssuanceId
     * @throws RuntimeException if the issuance ID cannot be found
     */
    private static MpTokenIssuanceId retrieveMptIssuanceId(
            XrplClient xrplClient,
            SingleSignedTransaction<MpTokenIssuanceCreate> signedTx) {

        try {
            return xrplClient.transaction(
                    TransactionRequestParams.of(signedTx.hash()),
                    MpTokenIssuanceCreate.class)
                    .metadata()
                    .orElseThrow(() -> new RuntimeException("Transaction metadata not found"))
                    .mpTokenIssuanceId()
                    .orElseThrow(() -> new RuntimeException(
                            "Transaction metadata did not contain issuance ID"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve MPT Issuance ID: " + e.getMessage(), e);
        }
    }

    // ============================================================================
    // MPT Authorization
    // ============================================================================

    /**
     * Authorizes a recipient account to receive MPT tokens.
     * Before tokens can be transferred, the recipient must authorize receipt
     * of tokens for this specific issuance.
     * 
     * @param xrplClient       The XRPL client
     * @param signatureService Service for signing transactions
     * @param recipient        The account that will receive tokens
     * @param mptIssuanceId    The ID of the MPT issuance
     * @throws Exception if authorization fails
     */
    private static void authorizeMptHolder(
            XrplClient xrplClient,
            SignatureService<PrivateKey> signatureService,
            AccountDetails recipient,
            MpTokenIssuanceId mptIssuanceId) throws Exception {

        System.out.println("\n--- Step 2: Authorizing MPT Holder ---");

        // Get current account sequence number
        AccountInfoResult accountInfo = getAccountInfo(xrplClient, recipient.address);

        // Build the authorization transaction
        MpTokenAuthorize mpTokenAuthorize = MpTokenAuthorize.builder()
                .account(recipient.address) // Recipient's address
                .mpTokenIssuanceId(mptIssuanceId) // The token to authorize
                .signingPublicKey(recipient.keyPair.publicKey()) // Public key for verification
                .fee(XrpCurrencyAmount.ofDrops(TRANSACTION_FEE_DROPS)) // Transaction fee
                .sequence(accountInfo.accountData().sequence()) // Account sequence number
                .build();

        // Sign and submit the authorization
        SingleSignedTransaction<MpTokenAuthorize> signedAuthorizeTx = signatureService.sign(recipient.privateKey,
                mpTokenAuthorize);

        SubmitResult<MpTokenAuthorize> authorizeResult = xrplClient.submit(signedAuthorizeTx);

        // Display authorization results
        printTransactionResult("MPT Authorize", authorizeResult);

        // Wait for transaction to be processed before retrieving issuance ID
        System.out.println("Waiting " + TRANSACTION_WAIT_TIME_SECONDS + " seconds for transaction to be processed...");
        TimeUnit.SECONDS.sleep(TRANSACTION_WAIT_TIME_SECONDS);

    }

    // ============================================================================
    // MPT Transfer
    // ============================================================================

    /**
     * Transfers MPT tokens from the issuer to the recipient.
     * This uses a Payment transaction with MPT currency amount.
     * 
     * @param xrplClient       The XRPL client
     * @param signatureService Service for signing transactions
     * @param issuer           The account sending tokens
     * @param recipient        The account receiving tokens
     * @param mptIssuanceId    The ID of the MPT issuance to transfer
     * @throws Exception if transfer fails
     */
    private static void transferMptTokens(
            XrplClient xrplClient,
            SignatureService<PrivateKey> signatureService,
            AccountDetails issuer,
            AccountDetails recipient,
            MpTokenIssuanceId mptIssuanceId) throws Exception {

        System.out.println("\n--- Step 3: Transferring MPT Tokens ---");

        // Get current account sequence number
        AccountInfoResult accountInfo = getAccountInfo(xrplClient, issuer.address);

        // Build the payment transaction with MPT currency amount
        Payment payment = Payment.builder()
                .account(issuer.address) // Sender's address
                .amount(MptCurrencyAmount.builder()
                        .mptIssuanceId(mptIssuanceId) // The token to transfer
                        .value(TRANSFER_AMOUNT) // Amount to transfer
                        .build())
                .destination(recipient.address) // Recipient's address
                .signingPublicKey(issuer.keyPair.publicKey()) // Public key for verification
                .fee(XrpCurrencyAmount.ofDrops(TRANSACTION_FEE_DROPS)) // Transaction fee
                .sequence(accountInfo.accountData().sequence()) // Account sequence number
                .build();

        // Sign and submit the transfer
        SingleSignedTransaction<Payment> signedTransferTx = signatureService.sign(issuer.privateKey, payment);

        SubmitResult<Payment> transferResult = xrplClient.submit(signedTransferTx);

        // Display transfer results
        printTransactionResult("MPT Transfer", transferResult);
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    /**
     * Retrieves account information from the XRPL.
     * This is needed to get the current sequence number for transactions.
     * 
     * @param xrplClient The XRPL client
     * @param address    The account address to query
     * @return AccountInfoResult containing account details
     * @throws RuntimeException if account info cannot be retrieved
     */
    private static AccountInfoResult getAccountInfo(XrplClient xrplClient, Address address) {
        AccountInfoRequestParams accountInfoParams = AccountInfoRequestParams.of(address);
        try {
            return xrplClient.accountInfo(accountInfoParams);
        } catch (Exception e) {
            String errorMsg = "Error getting account info for " + address.value() + ": " + e.getMessage();
            System.err.println(errorMsg);
            throw new RuntimeException(errorMsg, e);
        }
    }

    /**
     * Prints transaction submission results in a formatted way.
     * 
     * @param transactionType The type of transaction (for display)
     * @param result          The SubmitResult from the transaction
     */
    private static void printTransactionResult(String transactionType, SubmitResult<?> result) {
        System.out.println("--------------------------------");
        System.out.println(transactionType + " - Engine Result: " + result.engineResult());

        if (result.transactionResult() != null && result.transactionResult().hash() != null) {
            String transactionHash = result.transactionResult().hash().value();
            System.out.println(transactionType + " - Transaction Hash: " + transactionHash);
            System.out.println(transactionType + " - Explorer URL: " + TESTNET_EXPLORER_URL + transactionHash);
        }

        System.out.println("--------------------------------");
    }

    // ============================================================================
    // Helper Classes
    // ============================================================================

    /**
     * Container class to hold account-related information together.
     */
    private static class AccountDetails {
        final KeyPair keyPair;
        final PrivateKey privateKey;
        final Address address;

        AccountDetails(KeyPair keyPair, PrivateKey privateKey, Address address) {
            this.keyPair = keyPair;
            this.privateKey = privateKey;
            this.address = address;
        }
    }
}
