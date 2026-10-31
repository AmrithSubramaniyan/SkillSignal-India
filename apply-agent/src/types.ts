export type ApplyType = 'EASY_APPLY' | 'EXTERNAL_REDIRECT' | 'CLOSED_LISTING';

export type BlockerType =
  | 'CAPTCHA'
  | 'MFA'
  | 'UNKNOWN_QUESTION'
  | 'LEGAL_QUESTION'
  | 'VISA_QUESTION'
  | 'LOGIN_REQUIRED'
  | 'UNEXPECTED_PAGE';

export interface ScreeningQuestion {
  id: string;
  label: string;
  category: 'FACTUAL' | 'PREFERENCE' | 'NUMERIC' | 'LEGAL_CONSENT' | 'FREE_TEXT' | 'UNKNOWN';
}

export interface BlockerDetection {
  blocked: boolean;
  blockerType?: BlockerType;
  message?: string;
}

export interface ConfirmationDetection {
  confirmed: boolean;
  confirmationSignal?: string;
}

/**
 * Stage 28 Portal Adapter Interface (F14)
 * Never stores or transmits portal passwords or cookies to the server.
 */
export interface PortalAdapter {
  readonly portalName: string;
  isLoggedIn(): Promise<boolean>;
  openJob(jobUrl: string): Promise<void>;
  detectApplyType(): Promise<ApplyType>;
  detectBlocker(): Promise<BlockerDetection>;
  readQuestions(): Promise<ScreeningQuestion[]>;
  fillAnswers(answers: Record<string, string>): Promise<string[]>;
  uploadResume(resumePath: string): Promise<boolean>;
  submit(): Promise<void>;
  detectConfirmation(): Promise<ConfirmationDetection>;
}

