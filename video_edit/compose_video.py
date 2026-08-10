#!/usr/bin/env python3
"""Pixel-preserving compositor for the Chiikawa pool edit.

No generative AI is used. Source pixels are extracted/masked and composited.
Expected inputs:
  assets/base.png       Final pool image whose lower-left character must remain untouched.
  assets/source.mp4     Source clip containing the upper-right character.
  assets/right_mask.png Optional 8-bit mask (white=character) for a chosen source frame.

The script deliberately never processes the lower-left region. Only RIGHT_ROI is replaced.
"""
from pathlib import Path
import argparse, math
import cv2
import numpy as np

RIGHT_ROI = (170, 340, 700, 1030)  # x1,y1,x2,y2 on 768x1365 base; tune after assets are added


def read_frame(video: Path, frame_no: int):
    cap = cv2.VideoCapture(str(video))
    cap.set(cv2.CAP_PROP_POS_FRAMES, frame_no)
    ok, frame = cap.read()
    cap.release()
    if not ok:
        raise RuntimeError(f"Cannot read frame {frame_no} from {video}")
    return frame


def auto_mask(frame, crop):
    """Conservative foreground seed; manual mask is preferred for exact character pixels."""
    x1,y1,x2,y2 = crop
    roi = frame[y1:y2, x1:x2]
    mask = np.zeros(roi.shape[:2], np.uint8)
    bg = np.zeros((1,65), np.float64); fg = np.zeros((1,65), np.float64)
    rect=(4,4,max(2,roi.shape[1]-8),max(2,roi.shape[0]-8))
    cv2.grabCut(roi,mask,rect,bg,fg,7,cv2.GC_INIT_WITH_RECT)
    return np.where((mask==cv2.GC_FGD)|(mask==cv2.GC_PR_FGD),255,0).astype(np.uint8)


def feather(mask, radius=1):
    if radius <= 0: return mask
    return cv2.GaussianBlur(mask,(0,0),radius)


def overlay(dst, src, mask, x, y):
    h,w=src.shape[:2]
    H,W=dst.shape[:2]
    x0=max(0,x); y0=max(0,y); x1=min(W,x+w); y1=min(H,y+h)
    if x0>=x1 or y0>=y1: return dst
    sx=x0-x; sy=y0-y
    s=src[sy:sy+y1-y0,sx:sx+x1-x0]
    m=mask[sy:sy+y1-y0,sx:sx+x1-x0].astype(np.float32)[...,None]/255.0
    d=dst[y0:y1,x0:x1].astype(np.float32)
    dst[y0:y1,x0:x1]=(s.astype(np.float32)*m+d*(1-m)).astype(np.uint8)
    return dst


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--base',default='assets/base.png')
    ap.add_argument('--source',default='assets/source.mp4')
    ap.add_argument('--mask',default='assets/right_mask.png')
    ap.add_argument('--source-frame',type=int,default=270)
    ap.add_argument('--seconds',type=float,default=6)
    ap.add_argument('--fps',type=int,default=30)
    ap.add_argument('--out',default='output/final.mp4')
    args=ap.parse_args()

    base=cv2.imread(args.base,cv2.IMREAD_COLOR)
    if base is None: raise FileNotFoundError(args.base)
    src_full=read_frame(Path(args.source),args.source_frame)
    x1,y1,x2,y2=RIGHT_ROI
    # Scale ROI coordinates if source differs from base reference size.
    sx=src_full.shape[1]/768.0; sy=src_full.shape[0]/1365.0
    cx1,cx2=int(x1*sx),int(x2*sx); cy1,cy2=int(y1*sy),int(y2*sy)
    src=src_full[cy1:cy2,cx1:cx2].copy()

    mp=Path(args.mask)
    if mp.exists():
        mask=cv2.imread(str(mp),cv2.IMREAD_GRAYSCALE)
        mask=cv2.resize(mask,(src.shape[1],src.shape[0]),interpolation=cv2.INTER_NEAREST)
    else:
        mask=auto_mask(src_full,(cx1,cy1,cx2,cy2))
    mask=feather(mask,0.7)

    # Fit extracted original pixels into the right-side target region. No redraw/recolor.
    target_w=int(base.shape[1]*0.58)
    scale=target_w/src.shape[1]
    src=cv2.resize(src,None,fx=scale,fy=scale,interpolation=cv2.INTER_LANCZOS4)
    mask=cv2.resize(mask,(src.shape[1],src.shape[0]),interpolation=cv2.INTER_LINEAR)
    anchor_x=int(base.shape[1]*0.39)
    anchor_y=int(base.shape[0]*0.22)

    outp=Path(args.out); outp.parent.mkdir(parents=True,exist_ok=True)
    writer=cv2.VideoWriter(str(outp),cv2.VideoWriter_fourcc(*'mp4v'),args.fps,(base.shape[1],base.shape[0]))
    n=max(1,round(args.seconds*args.fps))
    for i in range(n):
        t=i/args.fps
        frame=base.copy()  # guarantees lower-left character remains byte-for-byte from base
        # Gentle whole-character float only; source artwork itself is never warped/generated.
        dx=round(3*math.sin(2*math.pi*t/4.8))
        dy=round(5*math.sin(2*math.pi*t/3.6))
        frame=overlay(frame,src,mask,anchor_x+dx,anchor_y+dy)
        writer.write(frame)
    writer.release()
    print(outp)

if __name__=='__main__': main()
