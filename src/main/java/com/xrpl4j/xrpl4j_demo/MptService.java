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

public class MptService {

    public static void main(String[] args) throws Exception {

        // rsNnw5i5tbgyjedDRNSmBk2Jw7PHHziPJ5
        Base58EncodedSecret issuerSecret = Base58EncodedSecret.of("sEd7HFg4UKpa4UA6CJAxNLZcMF4kYbE");
        Seed issuerSeed = Seed.fromBase58EncodedSecret(issuerSecret);

        // rDirbKUBmzJnqNzFEo9KaqLzpz62B4PmJY
        Base58EncodedSecret recipientSecret = Base58EncodedSecret.of("sEdTKevpT15jdZBRgLcT3Ye8rvkrY8P");
        Seed recipientSeed = Seed.fromBase58EncodedSecret(recipientSecret);

        // Connect to XRPL Testnet
        XrplClient xrplClient = new XrplClient(HttpUrl.get("https://s.altnet.rippletest.net:51234/"));

        // Create signature service
        SignatureService<PrivateKey> signatureService = new BcSignatureService();

        // Create keypair and address
        KeyPair issuerKeyPair = issuerSeed.deriveKeyPair();
        PrivateKey issuerPrivateKey = issuerKeyPair.privateKey();
        Address issuerClassicAddress = issuerKeyPair.publicKey().deriveAddress();

        KeyPair holderKeyPair = recipientSeed.deriveKeyPair();
        PrivateKey holderPrivateKey = holderKeyPair.privateKey();
        Address holderClassicAddress = holderKeyPair.publicKey().deriveAddress();

        // Get the current sequence number for the account
        AccountInfoResult accountInfo = getAccountInfo(xrplClient, issuerClassicAddress);

        // Build the MPT transaction

        MpTokenIssuanceCreateFlags flags = MpTokenIssuanceCreateFlags.builder()
                .tfMptCanLock(true)
                .tfMptCanEscrow(true)
                .tfMptCanTrade(true)
                .tfMptCanTransfer(true)
                .tfMptCanClawback(true)
                .build();
        MpTokenMetadata mpTokenMetadata = MpTokenMetadata.of(Hex.encodeHexString("test".getBytes("UTF-8")));
        MpTokenIssuanceCreate mpTokenIssuanceCreate = MpTokenIssuanceCreate.builder()
                .account(issuerClassicAddress)
                .transferFee(TransferFee.of(UnsignedInteger.valueOf(100)))
                .assetScale(AssetScale.of(UnsignedInteger.valueOf(6)))
                .mpTokenMetadata(mpTokenMetadata)
                .maximumAmount(MpTokenNumericAmount.of(UnsignedLong.valueOf(1000)))
                .fee(XrpCurrencyAmount.ofDrops(12))
                .sequence(accountInfo.accountData().sequence())
                .signingPublicKey(issuerKeyPair.publicKey())
                .flags(flags)
                .build();

        // Sign the transaction
        // UnsignedTransaction<PaymentService> unsignedTx =
        // UnsignedTransaction.of(payment);
        SingleSignedTransaction<MpTokenIssuanceCreate> signedTx = signatureService.sign(issuerPrivateKey,
                mpTokenIssuanceCreate);

        // Submit to XRPL
        SubmitResult<MpTokenIssuanceCreate> result = xrplClient.submit(signedTx);

        System.out.println("Engine result: " + result.engineResult());
        System.out.println("Transaction hash: " + result.transactionResult().hash());
        System.out.println("Explorer URL: https://testnet.xrpl.org/transactions/" +
                result.transactionResult().hash().value());
        System.out.println("--------------------------------");

        System.out.println("Waiting for 5 seconds to allow the transaction to be processed...");
        TimeUnit.SECONDS.sleep(5);
        System.out.println("Done waiting");
        System.out.println("--------------------------------");

        MpTokenIssuanceId mpTokenIssuanceId = xrplClient.transaction(
                TransactionRequestParams.of(signedTx.hash()),
                MpTokenIssuanceCreate.class).metadata()
                .orElseThrow(RuntimeException::new)
                .mpTokenIssuanceId()
                .orElseThrow(() -> new RuntimeException("issuance create metadata did not contain issuance ID"));

        System.out.println("--------------------------------");
        System.out.println("MpTokenIssuanceId: " + mpTokenIssuanceId.value());
        System.out.println("--------------------------------");

        // MPTAuthorize
        AccountInfoResult accountInfoForAuthorize = getAccountInfo(xrplClient, holderClassicAddress);

        MpTokenAuthorize mpTokenAuthorize = MpTokenAuthorize.builder()
                .account(holderClassicAddress)
                .mpTokenIssuanceId(mpTokenIssuanceId)
                .signingPublicKey(holderKeyPair.publicKey())
                .fee(XrpCurrencyAmount.ofDrops(12))
                .sequence(accountInfoForAuthorize.accountData().sequence())
                .build();

        SingleSignedTransaction<MpTokenAuthorize> signedAuthorizeTx = signatureService.sign(holderPrivateKey,
                mpTokenAuthorize);

        SubmitResult<MpTokenAuthorize> authorizeResult = xrplClient.submit(signedAuthorizeTx);

        System.out.println("--------------------------------");
        System.out.println("Engine result: " + authorizeResult.engineResult());
        System.out.println("Transaction hash for MPT Authorize: " + authorizeResult.transactionResult().hash());
        System.out.println("Explorer URL for MPT Authorize: https://testnet.xrpl.org/transactions/" +
                authorizeResult.transactionResult().hash().value());

        // MPTTransfer with a Payment
        AccountInfoResult accountInfoForTransfer = getAccountInfo(xrplClient, issuerClassicAddress);
        Payment payment = Payment.builder()
                .account(issuerClassicAddress)
                .amount(MptCurrencyAmount.builder().mptIssuanceId(mpTokenIssuanceId).value("1000000")
                        .build())
                .destination(holderClassicAddress)
                .signingPublicKey(issuerKeyPair.publicKey())
                .fee(XrpCurrencyAmount.ofDrops(12))
                .sequence(accountInfoForTransfer.accountData().sequence())
                .build();

        SingleSignedTransaction<Payment> signedTransferTx = signatureService.sign(issuerPrivateKey, payment);

        SubmitResult<Payment> transferResult = xrplClient.submit(signedTransferTx);

        System.out.println("--------------------------------");
        System.out.println("Engine result: " + transferResult.engineResult());
        System.out.println("Transaction hash for MPT Transfer: " + transferResult.transactionResult().hash());
        System.out.println("Explorer URL for MPT Transfer: https://testnet.xrpl.org/transactions/" +
                transferResult.transactionResult().hash().value());

    }

    private static AccountInfoResult getAccountInfo(XrplClient xrplClient, Address address) {
        AccountInfoRequestParams accountInfoParams = AccountInfoRequestParams.of(address);
        try {
            return xrplClient.accountInfo(accountInfoParams);
        } catch (Exception e) {
            System.out.println("Error getting account info: " + e.getMessage());
            throw new RuntimeException("Error getting account info: " + e.getMessage());
        }
    }
}
