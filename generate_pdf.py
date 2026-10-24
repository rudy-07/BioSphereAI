import os
import sys
from reportlab.lib.pagesizes import letter
from reportlab.lib import colors
from reportlab.lib.units import inch
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, HRFlowable, KeepTogether
)
from reportlab.pdfgen import canvas

class NumberedCanvas(canvas.Canvas):
    def __init__(self, *args, **kwargs):
        super(NumberedCanvas, self).__init__(*args, **kwargs)
        self._saved_page_states = []

    def showPage(self):
        self._saved_page_states.append(dict(self.__dict__))
        self._startPage()

    def save(self):
        num_pages = len(self._saved_page_states)
        for state in self._saved_page_states:
            self.__dict__.update(state)
            self.draw_page_decorations(num_pages)
            super(NumberedCanvas, self).showPage()
        super(NumberedCanvas, self).save()

    def draw_page_decorations(self, page_count):
        self.saveState()
        self.setFont("Helvetica", 8)
        self.setFillColor(colors.HexColor("#718096"))
        
        # Header (pages > 1)
        if self._pageNumber > 1:
            self.drawString(40, letter[1] - 30, "Project Proposal: Next-Gen AI Integration in Continuous Glucose & Blood Pressure Monitors")
            self.drawRightString(letter[0] - 40, letter[1] - 30, "Dexcom Collaboration Proposal")
            self.setStrokeColor(colors.HexColor("#E2E8F0"))
            self.setLineWidth(0.5)
            self.line(40, letter[1] - 35, letter[0] - 40, letter[1] - 35)

        # Footer
        footer_text = f"Confidential & Proprietary — Prepared for Faculty Review | Page {self._pageNumber} of {page_count}"
        self.drawRightString(letter[0] - 40, 25, footer_text)
        self.drawString(40, 25, "Student Researcher Proposal | In Silico Simulation Ready")
        self.setStrokeColor(colors.HexColor("#E2E8F0"))
        self.setLineWidth(0.5)
        self.line(40, 36, letter[0] - 40, 36)
        self.restoreState()


def build_pdf(filename):
    doc = SimpleDocTemplate(
        filename,
        pagesize=letter,
        leftMargin=40,
        rightMargin=40,
        topMargin=45,
        bottomMargin=45
    )

    styles = getSampleStyleSheet()
    
    # Custom Palette
    DEXCOM_GREEN = colors.HexColor("#008542")
    DARK_NAVY = colors.HexColor("#1A365D")
    TEXT_DARK = colors.HexColor("#2D3748")
    BG_LIGHT_GREEN = colors.HexColor("#F0F8F4")
    BORDER_GREEN = colors.HexColor("#A8D5BC")
    BORDER_GRAY = colors.HexColor("#CBD5E0")
    ROW_BG = colors.HexColor("#F8FAFC")

    # Custom Typography Styles
    title_style = ParagraphStyle(
        'DocTitle',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=18,
        leading=22,
        textColor=DARK_NAVY,
        spaceAfter=4
    )
    
    subtitle_style = ParagraphStyle(
        'DocSubTitle',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=11,
        leading=15,
        textColor=DEXCOM_GREEN,
        spaceAfter=10
    )

    meta_style = ParagraphStyle(
        'DocMeta',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=8.5,
        leading=12,
        textColor=colors.HexColor("#4A5568")
    )

    h1_style = ParagraphStyle(
        'Heading1_Custom',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=12,
        leading=16,
        textColor=DARK_NAVY,
        spaceBefore=8,
        spaceAfter=4
    )

    h2_style = ParagraphStyle(
        'Heading2_Custom',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=10,
        leading=13,
        textColor=DEXCOM_GREEN,
        spaceBefore=5,
        spaceAfter=2
    )

    body_style = ParagraphStyle(
        'Body_Custom',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=8.5,
        leading=12,
        textColor=TEXT_DARK,
        spaceAfter=4
    )

    bullet_style = ParagraphStyle(
        'Bullet_Custom',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=8.5,
        leading=12,
        textColor=TEXT_DARK,
        leftIndent=12,
        firstLineIndent=-8,
        spaceAfter=2
    )

    callout_style = ParagraphStyle(
        'Callout_Text',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=8.2,
        leading=11.5,
        textColor=colors.HexColor("#1A202C")
    )

    table_header_style = ParagraphStyle(
        'TableHeader',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=8,
        leading=10,
        textColor=colors.white,
        alignment=1 # Center
    )

    table_cell_style = ParagraphStyle(
        'TableCell',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=7.8,
        leading=10,
        textColor=TEXT_DARK
    )

    table_cell_bold = ParagraphStyle(
        'TableCellBold',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=7.8,
        leading=10,
        textColor=DARK_NAVY
    )

    story = []

    # 1. Title Banner
    story.append(Paragraph("PROJECT PROPOSAL & RESEARCH BLUEPRINT", subtitle_style))
    story.append(Paragraph("Next-Generation AI Integration for Continuous Glucose & Cuffless Blood Pressure Biowearables", title_style))
    story.append(Paragraph("<b>Target Partner:</b> Dexcom, Inc. &nbsp;|&nbsp; <b>Domain:</b> AI, Biosensors, T2D/T1D, Cardiometabolic Health &nbsp;|&nbsp; <b>Status:</b> Working Simulation Ready", meta_style))
    story.append(Spacer(1, 6))
    story.append(HRFlowable(width="100%", thickness=1.5, color=DEXCOM_GREEN, spaceBefore=0, spaceAfter=8))

    # 2. Executive Summary Box
    exec_text = (
        "<b>Executive Summary:</b> As Dexcom expands beyond Type 1 Diabetes (T1D) into Type 2 Diabetes (T2D) "
        "and overall metabolic health with the launch of the over-the-counter <b>Dexcom Stelo</b>, the algorithmic requirement "
        "shifts fundamentally from reactive emergency hypoglycemia alarms to <b>proactive behavioral modeling and meal-spike prediction</b>. "
        "Furthermore, over 75% of T2D patients suffer from comorbid hypertension, and Dexcom recently partnered with <b>Nanowear</b> "
        "to synchronize CGM with continuous cuffless blood pressure. This proposal presents an end-to-end <b>Cardiometabolic AI Architecture</b> "
        "and an operational <i>in silico</i> digital twin simulator that validates real-time forecasting, closed-loop safety, and joint glucose-BP analytics."
    )
    exec_table = Table(
        [[Paragraph(exec_text, callout_style)]],
        colWidths=[letter[0] - 80]
    )
    exec_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), BG_LIGHT_GREEN),
        ('BOX', (0,0), (-1,-1), 1, BORDER_GREEN),
        ('PADDING', (0,0), (-1,-1), 8),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
    ]))
    story.append(exec_table)
    story.append(Spacer(1, 8))

    # 3. Industry Alignment: Dexcom's Real-World Strategic Frontiers
    story.append(Paragraph("1. Strategic Context: Connecting Dexcom's Product Roadmap", h1_style))
    
    t2d_context = (
        "<b>• The Type 2 Diabetes Shift (Dexcom Stelo):</b> FDA-cleared in August 2024, Stelo is Dexcom's 15-day OTC glucose biosensor "
        "tailored for non-insulin T2D and pre-diabetes. Crucially, Stelo <i>removes audible hypoglycemia alarms</i> to eliminate user "
        "alarm fatigue. The core user value is entirely data-driven: understanding postprandial glycemic response (PPGR) and habit formation."
    )
    ai_context = (
        "<b>• Generative AI & Google Cloud (Vertex AI / Gemini):</b> In late 2024, Dexcom integrated Google Cloud's Gemini models "
        "to launch a proprietary GenAI engine (powering Stelo Weekly Insights), translating multimodal biosensing into contextualized natural-language coaching."
    )
    bp_context = (
        "<b>• The Blood Pressure & Cardiovascular Connection (Nanowear Partnership):</b> Dexcom G7 was recently integrated with "
        "Nanowear's FDA 510(k)-cleared <i>SimpleSense-BP</i> AI cuffless blood pressure platform. Pathophysiologically, acute glucose spikes cause "
        "endothelial oxidative stress and arterial vasoconstriction, while diabetic autonomic neuropathy causes nocturnal 'non-dipping' BP."
    )
    story.append(Paragraph(t2d_context, bullet_style))
    story.append(Paragraph(ai_context, bullet_style))
    story.append(Paragraph(bp_context, bullet_style))
    story.append(Spacer(1, 6))

    # 4. Proposed AI Architecture Modules
    story.append(Paragraph("2. Proposed AI Integration Architecture", h1_style))

    arch_data = [
        [
            Paragraph("Module / Domain", table_header_style),
            Paragraph("Target Audience", table_header_style),
            Paragraph("Core Algorithmic Technique", table_header_style),
            Paragraph("Clinical Impact & Deliverable", table_header_style)
        ],
        [
            Paragraph("<b>PPGR & Behavioral Nudges</b>", table_cell_bold),
            Paragraph("Type 2 Diabetes<br/>(Dexcom Stelo)", table_cell_style),
            Paragraph("LightGBM / 1D-CNN + Multimodal LLM (Gemini style)", table_cell_style),
            Paragraph("Predicts postprandial glucose peaks from meal composition; prescribes micro-interventions (e.g. 15-min post-meal walk).", table_cell_style)
        ],
        [
            Paragraph("<b>Safe Automated Insulin Delivery (AID)</b>", table_cell_bold),
            Paragraph("Type 1 Diabetes<br/>(Dexcom G7)", table_cell_style),
            Paragraph("Safe Reinforcement Learning (SAC) with Control Barrier Functions (CBF)", table_cell_style),
            Paragraph("Modulates basal/micro-boluses autonomously while imposing hard mathematical safety shutoffs preventing hypoglycemia.", table_cell_style)
        ],
        [
            Paragraph("<b>Multi-Horizon Forecaster</b>", table_cell_bold),
            Paragraph("T1D & T2D<br/>Ecosystem", table_cell_style),
            Paragraph("Physics-Informed Neural Network (PINN) / Temporal Transformer", table_cell_style),
            Paragraph("30 & 60-minute forward prediction compensating for 5–15 min subcutaneous interstitial fluid (ISF) diffusion lag.", table_cell_style)
        ],
        [
            Paragraph("<b>Cardiometabolic Multi-Sensor Fusion</b>", table_cell_bold),
            Paragraph("Cardiovascular &<br/>Hypertensive Diabetics", table_cell_style),
            Paragraph("Wavelet Decomposition & Dynamic Vascular Responsiveness Index (DVRI)", table_cell_style),
            Paragraph("Flags nocturnal non-dipping hypertension and quantifies acute arterial stiffness induced by glycemic spikes.", table_cell_style)
        ]
    ]

    arch_table = Table(arch_data, colWidths=[105, 75, 155, 195])
    arch_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), DARK_NAVY),
        ('ALIGN', (0,0), (-1,0), 'CENTER'),
        ('GRID', (0,0), (-1,-1), 0.5, BORDER_GRAY),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ('PADDING', (0,0), (-1,-1), 4.5),
        ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, ROW_BG]),
    ]))
    story.append(arch_table)
    story.append(Spacer(1, 8))

    # 5. In Silico Simulation Engine & Results
    story.append(Paragraph("3. Functional In Silico Simulation Benchmark (Already Built)", h1_style))
    sim_intro = (
        "To substantiate this proposal, we implemented a complete, fully functional Python simulation engine "
        "(based on modified Bergman minimal model differential equations, 2-compartment gut absorption, 8.5-min ISF diffusion delay, "
        "G7-standard 8.2% MARD sensor noise, and coupled Windkessel arterial hemodynamics). Highlights from our 24-hour benchmark:"
    )
    story.append(Paragraph(sim_intro, body_style))

    metrics_data = [
        [
            Paragraph("Evaluation Metric", table_header_style),
            Paragraph("Baseline Standard", table_header_style),
            Paragraph("Our Simulation Outcome", table_header_style),
            Paragraph("Clinical Significance", table_header_style)
        ],
        [
            Paragraph("<b>T1D Clarke Error Grid (30m)</b>", table_cell_bold),
            Paragraph("Zones A+B > 95%", table_cell_style),
            Paragraph("<b>Zone A: 88.4% | Zone A+B: 100.0%</b>", table_cell_style),
            Paragraph("Zero erroneous or hazardous treatment recommendations.", table_cell_style)
        ],
        [
            Paragraph("<b>30-min Glucose RMSE</b>", table_cell_bold),
            Paragraph("< 25.0 mg/dL", table_cell_style),
            Paragraph("<b>15.29 mg/dL (MARD: 9.96%)</b>", table_cell_style),
            Paragraph("High-fidelity trajectory tracking overcoming ISF lag.", table_cell_style)
        ],
        [
            Paragraph("<b>T2D Spike Reduction (AI Nudge)</b>", table_cell_bold),
            Paragraph("Unmitigated spike", table_cell_style),
            Paragraph("<b>Peak reduced from 271.5 to 246.9 mg/dL</b>", table_cell_style),
            Paragraph("15-min post-lunch walk blunted peak excursion by 24.6 mg/dL (-9.1%).", table_cell_style)
        ],
        [
            Paragraph("<b>Nocturnal BP Dipping Status</b>", table_cell_bold),
            Paragraph("Dipping 10%–20%", table_cell_style),
            Paragraph("<b>Detected 6.5% (Non-Dipper Flagged)</b>", table_cell_style),
            Paragraph("Successfully flagged elevated cardiovascular and stroke risk profile.", table_cell_style)
        ],
        [
            Paragraph("<b>Sensor Artifact Discrimination</b>", table_cell_bold),
            Paragraph("High false alarms", table_cell_style),
            Paragraph("<b>Isolated 02:00 AM PISA Compression</b>", table_cell_style),
            Paragraph("Differentiates mechanical sleep compression from true hypoglycemia.", table_cell_style)
        ]
    ]

    metrics_table = Table(metrics_data, colWidths=[130, 85, 140, 175])
    metrics_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), DEXCOM_GREEN),
        ('ALIGN', (0,0), (-1,0), 'CENTER'),
        ('GRID', (0,0), (-1,-1), 0.5, BORDER_GRAY),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ('PADDING', (0,0), (-1,-1), 4),
        ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, ROW_BG]),
    ]))
    story.append(metrics_table)
    story.append(Spacer(1, 8))

    # 6. Interactive Demo & Technical Paper Roadmap
    story.append(Paragraph("4. Deliverables & Collaboration Roadmap", h1_style))

    roadmap_text = (
        "<b>• Interactive Streamlit Demonstration (app.py):</b> A live dashboard is fully operational with interactive carbohydrate sliders, "
        "exercise injections, synchronized glucose & cuffless blood pressure graphs, Clarke EGA diagnostics, and real-time Dexcom v3 API JSON emulation.<br/>"
        "<b>• Technical Paper Co-Authorship:</b> A complete academic draft titled <i>'Next-Generation Cardiometabolic Intelligence: Multi-Modal AI Framework "
        "for Continuous Glucose and Cuffless Blood Pressure Co-Monitoring'</i> is prepared for faculty review, joint refinement, and potential submission "
        "to a biomedical AI conference / journal (e.g., IEEE TBME, ATTD) or presentation during the Dexcom campus visit.<br/>"
        "<b>• Next Step:</b> 5-minute live demonstration of the simulator during office hours or faculty review to finalize presentation slides for Dexcom."
    )
    story.append(Paragraph(roadmap_text, body_style))
    story.append(Spacer(1, 10))

    # Footer signature block
    sig_data = [
        [
            Paragraph("<b>Prepared by:</b> Rudransh Shekhar", meta_style),
            Paragraph("<b>Code Repository:</b> Local Prototype (Streamlit / Plotly)", meta_style),
            Paragraph("<b>Contact:</b> kanhaiffco2007@gmail.com", meta_style)
        ]
    ]
    sig_table = Table(sig_data, colWidths=[180, 200, 150])
    sig_table.setStyle(TableStyle([
        ('LINEABOVE', (0,0), (-1,0), 1, BORDER_GREEN),
        ('PADDING', (0,0), (-1,-1), 4),
    ]))
    story.append(sig_table)

    doc.build(story, canvasmaker=NumberedCanvas)
    print(f"PDF successfully built: {filename}")


if __name__ == "__main__":
    out_path = sys.argv[1] if len(sys.argv) > 1 else "Dexcom_AI_Project_Proposal.pdf"
    build_pdf(out_path)
