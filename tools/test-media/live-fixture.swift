// Author-owned synthetic moving RGB bars. macOS AVFoundation only; not needed to run CI.
import AVFoundation
import CoreVideo
import Foundation
let output = URL(fileURLWithPath: CommandLine.arguments[1])
let writer = try AVAssetWriter(outputURL: output, fileType: .mp4)
let input = AVAssetWriterInput(mediaType: .video, outputSettings: [AVVideoCodecKey: AVVideoCodecType.h264, AVVideoWidthKey: 320, AVVideoHeightKey: 180, AVVideoCompressionPropertiesKey: [AVVideoAverageBitRateKey: 48000, AVVideoProfileLevelKey: AVVideoProfileLevelH264BaselineAutoLevel]])
let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32ARGB, kCVPixelBufferWidthKey as String: 320, kCVPixelBufferHeightKey as String: 180])
writer.add(input); writer.startWriting(); writer.startSession(atSourceTime: .zero)
for frame in 0..<225 {
    while !input.isReadyForMoreMediaData { Thread.sleep(forTimeInterval: 0.001) }
    var buffer: CVPixelBuffer?; CVPixelBufferCreate(kCFAllocatorDefault, 320, 180, kCVPixelFormatType_32ARGB, nil, &buffer)
    let pixel = buffer!; CVPixelBufferLockBaseAddress(pixel, [])
    let bytes = CVPixelBufferGetBaseAddress(pixel)!.assumingMemoryBound(to: UInt8.self)
    let stride = CVPixelBufferGetBytesPerRow(pixel)
    for y in 0..<180 { for x in 0..<320 {
        let p = y * stride + x * 4
        bytes[p] = 255; bytes[p+1] = UInt8((x + frame * 3) % 256); bytes[p+2] = UInt8(y % 256); bytes[p+3] = 160
    }}
    CVPixelBufferUnlockBaseAddress(pixel, [])
    precondition(adaptor.append(pixel, withPresentationTime: CMTime(value: Int64(frame), timescale: 5)))
}
input.markAsFinished()
let done = DispatchSemaphore(value: 0); writer.finishWriting { done.signal() }; done.wait()
precondition(writer.status == .completed)
let composition = AVMutableComposition()
let video = AVURLAsset(url: output)
try composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid)!.insertTimeRange(CMTimeRange(start: .zero, duration: CMTime(seconds: 45, preferredTimescale: 5)), of: video.tracks(withMediaType: .video)[0], at: .zero)
let audio = AVURLAsset(url: URL(fileURLWithPath: CommandLine.arguments[2]))
try composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid)!.insertTimeRange(CMTimeRange(start: .zero, duration: CMTime(seconds: 45, preferredTimescale: 44100)), of: audio.tracks(withMediaType: .audio)[0], at: .zero)
let export = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetPassthrough)!
export.outputURL = URL(fileURLWithPath: CommandLine.arguments[3]); export.outputFileType = .mp4
let merged = DispatchSemaphore(value: 0); export.exportAsynchronously { merged.signal() }; merged.wait()
precondition(export.status == .completed)
