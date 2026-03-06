# EC2 S3-Based File Transfer Design

## Objective

Replace the current SSH-based file transfer mechanism (rsync/scp) for AWS EC2 instances
with an S3-based alternative, using an S3 bucket as a staging area. This removes the
requirement for SSH-accessible security groups for file transfer and enables uploading
input files before the instance is running.

## Problem

The current mechanism for transferring input files to AWS EC2 instances and retrieving
output files uses `rsync` (for upload) and `scp` (for download) over SSH. This has two
significant disadvantages:

1. **Security**: Requires the EC2 security group to allow inbound SSH, which increases
   the attack surface of the instance.
2. **Latency**: Files can only be transferred after the instance is running and SSH-accessible,
   adding wall-clock time to every job.

## Design Overview

### Key Decisions and Rationale

1. **S3 as intermediary rather than direct instance transfer**: Using S3 decouples the
   transfer from the instance lifecycle. The local machine uploads to S3 using the AWS SDK
   (`s3client`, already available in `AWSEC2CommandExecutor`), and the instance pulls from
   S3 using the `aws` CLI. This avoids needing SSH for transfers entirely.

2. **New `transferMode` config key rather than replacing existing behaviour**: A new
   `transferMode: 's3'` option is introduced alongside the existing `transfer: true` flag.
   When absent or set to `'ssh'`, the existing rsync/scp behaviour is preserved unchanged.
   This ensures full backward compatibility.

3. **Two-phase implementation**: Phase 1 keeps the same execution ordering (transfer after
   instance is running) but uses S3 instead of SSH. Phase 2 reorders the flow so that S3
   upload happens before instance acquisition, which is a small incremental change on top
   of Phase 1.

4. **Instance pulls inputs via separate SSH call, pushes outputs in command wrapper**: The
   S3 input pull is executed as a separate SSH command after `acquireInstance`/`waitForSSH`,
   before `startCommand`. This cleanly separates transfer failures from command failures
   and produces distinct error messages. The output push remains in the command wrapper
   script (postamble) because it must run on the instance after the command completes and
   before termination — doing this via SSH from the local side would be fragile if
   connectivity is lost. The instance needs the `aws` CLI and an IAM instance profile with
   S3 access. The `CloudExecutor` base class uses provider-agnostic naming
   (`pullInputsFromBucket`) so that other cloud providers (e.g. GCP with GCS) can
   implement the same pattern.

5. **S3 staging cleanup in `cleanup()`**: Staging data is deleted from S3 after successful
   `transferFrom`, preventing accumulation of stale data.

6. **Use `TransferManager` for uploads**: Rather than raw `putObject()` (which has a 5GB
   limit), we use the AWS SDK `TransferManager` which automatically handles multipart
   uploads for large files. This is essential since genomics files routinely exceed 5GB.
   The `TransferManager` is created on demand and shut down after use without closing the
   underlying `s3client`.

## Configuration

The feature is activated by setting `transferMode` to `'s3'` alongside the existing
`transfer: true` setting. A new required parameter `transferBucket` specifies the S3
bucket to use.

```groovy
// bpipe.config
executor = "AWSEC2"

AWSEC2 {
    // ... existing config (accessKey, accessSecret, region, keypair, user, image, etc.)

    transfer = true
    transferMode = 's3'              // new: 'ssh' (default/current behaviour) or 's3'
    transferBucket = 'my-bpipe-staging'  // new: required when transferMode is 's3'
    transferPrefix = 'bpipe-jobs'    // new: optional, defaults to "bpipe/<pipeline_id>"
}
```

When `transferMode` is absent or set to `'ssh'`, the existing rsync/scp behaviour is
preserved with no changes.

## Architecture Overview

```
Phase 1 (instance running):

  Local ──s3client.putObject──▶ S3 Bucket ──aws s3 sync──▶ EC2 Instance
  Local ◀──s3client.getObject── S3 Bucket ◀──aws s3 sync── EC2 Instance

Phase 2 (pre-instance upload):

  Local ──s3client.putObject──▶ S3 Bucket
                                    │
                              acquireInstance()
                                    │
                               EC2 Instance ──aws s3 sync──▶ local disk
```

## Phase 1: S3 Transfer While Instance Is Running

### Goal

Replace rsync/scp with S3 as the transfer intermediary, removing the need for SSH-based
file transfer while keeping the same execution ordering.

### Changes

#### 1. `AWSEC2CommandExecutor` — New Fields

Add fields to track the S3 staging location:

```groovy
String transferBucket
String transferPrefix
```

These are populated from config during `acquireInstance()` or `connectInstance()`.

#### 2. `AWSEC2CommandExecutor.transferTo()` — S3 Upload Path

When `transferMode == 's3'`, instead of using rsync over SSH:

- Use the AWS SDK `TransferManager` (backed by the existing `s3client`) to upload each
  input file to `s3://<transferBucket>/<transferPrefix>/inputs/<absolute-path>`.
  `TransferManager` automatically handles multipart uploads for files larger than 5GB,
  which is essential for large genomics files.
- Preserve the full absolute path as the S3 key so that directory structure is maintained
  on the remote side

Pseudocode:
```groovy
void transferToS3(List<PipelineFile> fileList) {
    String prefix = resolveTransferPrefix()
    for(PipelineFile f : fileList) {
        String key = prefix + '/inputs' + f.toPath().toAbsolutePath().toString()
        s3client.putObject(transferBucket, key, f.toPath().toFile())
    }
}
```

#### 3. S3 Input Pull (separate SSH) and Output Push (command wrapper postamble)

The S3 input pull is executed as a **separate SSH call** in `transferFiles()` after the
instance is running, before `startCommand()`. This cleanly separates transfer errors from
command errors.

```bash
# Executed via SSH before startCommand
aws s3 sync s3://<bucket>/<prefix>/inputs/ / --quiet
```

The S3 output push is added as a **postamble** in the command wrapper script generated by
`startCommand()`:

```bash
# ... existing command execution ...

# Postamble - push outputs (only on success)
BPIPE_EXIT_CODE=$(cat <exitFile>)
if [ "$BPIPE_EXIT_CODE" = "0" ]; then
    for output in <output_files>; do
        aws s3 cp "$output" s3://<bucket>/<prefix>/outputs"$output"
    done
fi
```

The instance must have:
- The `aws` CLI installed on the AMI
- An IAM instance profile with `s3:GetObject`, `s3:PutObject`, and `s3:ListBucket`
  permissions on the transfer bucket

#### 4. `AWSEC2CommandExecutor.transferFrom()` — S3 Download Path

When `transferMode == 's3'`, instead of using scp:

- Use `s3client` to download each output file from
  `s3://<transferBucket>/<transferPrefix>/outputs/<absolute-path>`
  to its expected local path

Pseudocode:
```groovy
void transferFromS3(Map config, List<PipelineFile> fileList) {
    String prefix = resolveTransferPrefix()
    for(PipelineFile f : fileList) {
        String key = prefix + '/outputs' + f.toPath().toAbsolutePath().toString()
        S3Object obj = s3client.getObject(transferBucket, key)
        // Write obj.getObjectContent() to local file
    }
}
```

#### 5. `AWSEC2CommandExecutor.cleanup()` — S3 Staging Cleanup

After `transferFrom` completes successfully, delete the S3 prefix to avoid accumulating
stale staging data:

```groovy
void cleanupS3Staging() {
    String prefix = resolveTransferPrefix()
    // List and delete all objects under s3://<bucket>/<prefix>/
}
```

#### 6. Config Validation

Add validation in `validateConfig()`:
- If `transferMode == 's3'`, require `transferBucket` to be set
- Warn if no `instanceProfile` is configured (the instance needs S3 access)

### Files Modified (Phase 1)

| File | Change |
|------|--------|
| `AWSEC2CommandExecutor.groovy` | Add `transferBucket`, `transferPrefix` fields |
| `AWSEC2CommandExecutor.groovy` | Modify `transferTo()` to branch on `transferMode` |
| `AWSEC2CommandExecutor.groovy` | Modify `transferFrom()` to branch on `transferMode` |
| `AWSEC2CommandExecutor.groovy` | Modify `startCommand()` to add S3 pull/push to wrapper |
| `AWSEC2CommandExecutor.groovy` | Add `cleanupS3Staging()`, call from `cleanup()` |
| `AWSEC2CommandExecutor.groovy` | Add validation for S3 config in `validateConfig()` |

### Estimated Complexity

- ~150-200 lines of new/modified code
- Low risk: `s3client` already exists, SDK operations are straightforward
- Main risk: ensuring AMI has `aws` CLI and correct IAM permissions

## Phase 2: Pre-Instance S3 Upload

### Goal

Upload input files to S3 **before** the instance is acquired, so that instance startup
and file upload happen in parallel (or file upload completes before the instance is even
requested).

### Changes

#### 1. `CloudExecutor.start()` — Reorder Transfer Step

Currently the flow in `start()` is:

```
acquireInstance → waitForSSH → mountStorage → transferFiles → startCommand
```

For S3 transfer mode, change to:

```
transferFiles → acquireInstance → waitForSSH → mountStorage → startCommand
```

The change is small because `transferTo` in S3 mode only needs `s3client`, which can be
created from config without an instance. The `createClient()` method already accepts a
config map and can be called independently.

Concrete change in `CloudExecutor.start()`:

```groovy
// Before acquiring instance, do S3 upload if applicable
if(isS3TransferMode(cfg)) {
    this.prepareS3Client(cfg)
    this.transferFiles(cfg, cmd.inputs)
}

// ... acquire instance, wait for SSH, mount storage ...

// After instance is ready, do SSH transfer if applicable
if(!isS3TransferMode(cfg)) {
    this.transferFiles(cfg, cmd.inputs)
}
```

#### 2. `AWSEC2CommandExecutor` — Extract Client Creation

Ensure `createClient()` can be called before `acquireInstance()`. Currently it is called
inside `acquireInstance()`, but it is already a standalone method that takes a config map.
Add a thin `prepareS3Client()` method that calls `createClient()` if not already called,
so that `s3client` is available for the pre-upload.

### Files Modified (Phase 2, incremental on Phase 1)

| File | Change |
|------|--------|
| `CloudExecutor.groovy` | Reorder `transferFiles` call in `start()` |
| `CloudExecutor.groovy` | Add `isS3TransferMode()` helper (or make abstract) |
| `AWSEC2CommandExecutor.groovy` | Add `prepareS3Client()` method |

### Estimated Complexity

- ~20-30 lines of new/modified code on top of Phase 1
- Low risk: the reordering is straightforward and only affects S3 mode

## Backward Compatibility

- Fully backward compatible
- Default `transferMode` is `'ssh'` (or absent), preserving current behaviour
- No changes to existing config schemas; new keys are only required when `transferMode: 's3'`
- The `transfer: true` flag remains the master switch for any file transfer

## Prerequisites for Users

When using `transferMode: 's3'`:

1. An S3 bucket must exist and be accessible from both the local machine and the EC2 instance
2. The EC2 AMI must have the `aws` CLI installed
3. The EC2 instance must have an IAM instance profile with S3 read/write access to the
   transfer bucket (configured via the existing `instanceProfile` setting)
4. The local machine must have S3 access via the configured `accessKey`/`accessSecret`

## Implementation Steps

### Phase 1: S3 Transfer While Instance Is Running

- [x] **Step 1: Add `transferBucket` and `transferPrefix` fields to `AWSEC2CommandExecutor`**
  Add two new `String` fields and a `resolveTransferPrefix()` helper method that returns
  `transferPrefix ?: "bpipe/${pipelineId}"`. Populate these fields from config in
  `acquireInstance()` and `connectInstance()`. Add config validation in `validateConfig()`
  to require `transferBucket` when `transferMode == 's3'` and warn if `instanceProfile`
  is not set. Compile and verify existing tests still pass.

- [x] **Step 2: Add `transferToS3()` method and branch `transferTo()`**
  Add a new `transferToS3(List<PipelineFile>)` method that uploads each file to
  `s3://<transferBucket>/<prefix>/inputs/<absolute-path>` using `TransferManager` (which
  automatically handles multipart uploads for files >5GB). Modify `transferTo()` to check
  `command.processedConfig.transferMode` and delegate to either the existing rsync logic
  or the new `transferToS3()`. Compile and verify existing tests still pass.

- [x] **Step 3: Add S3 input pull via SSH and output push postamble in `startCommand()`**
  Add a `pullInputsFromBucket()` method (overriding the no-op default in `CloudExecutor`)
  that executes `aws s3 sync` via SSH to pull staged inputs from S3 to the instance.
  Call this from `transferFiles()` in `CloudExecutor` when `transferMode == 's3'`.
  In `startCommand()`, when `transferMode == 's3'`, append
  a postamble to `cmdText` that pushes output files to S3 after the command exits
  successfully. The input pull is separate from the command so that transfer failures
  produce distinct errors. Compile and verify.

- [ ] **Step 4: Add `transferFromS3()` method and branch `transferFrom()`**
  Add a new `transferFromS3(Map, List<PipelineFile>)` method that downloads each output
  file from `s3://<transferBucket>/<prefix>/outputs/<absolute-path>` using
  `s3client.getObject()` and writes it to the expected local path. Modify `transferFrom()`
  to branch on `transferMode`. Compile and verify.

- [ ] **Step 5: Add `cleanupS3Staging()` and call from `cleanup()`**
  Add a method that lists and deletes all objects under `s3://<bucket>/<prefix>/`. Call it
  from `cleanup()` after `transferFrom` completes successfully (i.e., after
  `super.cleanup()` in `AWSEC2CommandExecutor.cleanup()`). Compile and verify.

- [ ] **Step 6: Add unit tests for S3 transfer logic**
  Add tests in `test-src/` that mock `s3client` and verify: (a) `transferToS3` uploads
  files with correct keys, (b) `transferFromS3` downloads to correct local paths,
  (c) `cleanupS3Staging` deletes the right prefix, (d) config validation rejects missing
  `transferBucket` when `transferMode == 's3'`.

### Phase 2: Pre-Instance S3 Upload

- [ ] **Step 7: Add `prepareS3Client()` to `AWSEC2CommandExecutor`**
  Add a method that calls `createClient(config)` if `s3client` is null, so that the S3
  client can be initialised before `acquireInstance()` is called. Compile and verify.

- [ ] **Step 8: Add `isS3TransferMode()` helper to `CloudExecutor`**
  Add a protected method `boolean isS3TransferMode(Map config)` that returns
  `config.transferMode == 's3'`. This keeps the mode check in one place. Compile and verify.

- [ ] **Step 9: Reorder `CloudExecutor.start()` for S3 pre-upload**
  Before the `acquireInstance()` block, add a check: if `isS3TransferMode(cfg)`, call
  `prepareS3Client(cfg)` (via a new abstract/default method on `CloudExecutor`) then
  `transferFiles(cfg, cmd.inputs)`. Move the existing `transferFiles` call inside an
  `else` branch so SSH transfer still happens at the original point. Compile and verify
  existing tests pass.

- [ ] **Step 10: Add functional test for S3 transfer**
  Create `tests/aws_s3_transfer/test.groovy` and `tests/aws_s3_transfer/run.sh` that
  configure an AWSEC2 executor with `transferMode: 's3'`, run a simple pipeline, and
  verify files are staged through S3. The test should skip gracefully if AWS credentials
  are not available.

### Manual Verification

- [ ] Confirm that security groups without SSH inbound still allow file transfer in S3 mode
- [ ] Confirm that Phase 2 pre-upload reduces total job wall-clock time
- [ ] Confirm that S3 staging area is cleaned up after job completion
- [ ] Confirm backward compatibility: `transferMode: 'ssh'` and absent `transferMode` both
  preserve existing rsync/scp behaviour
