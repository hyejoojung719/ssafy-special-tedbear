package com.ssafy.tedbear.domain.game.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.text.html.Option;
import javax.transaction.Transactional;

import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;

import com.ssafy.tedbear.domain.game.dto.ClueDto;
import com.ssafy.tedbear.domain.game.dto.CrossWordDto;
import com.ssafy.tedbear.domain.game.dto.Direction;
import com.ssafy.tedbear.domain.game.dto.GridDto;
import com.ssafy.tedbear.domain.game.dto.WordGameDto;
import com.ssafy.tedbear.domain.game.dto.WordGameResultDto;
import com.ssafy.tedbear.domain.game.repository.GameRecordRepository;
import com.ssafy.tedbear.domain.member.entity.Member;
import com.ssafy.tedbear.domain.member.service.MemberService;
import com.ssafy.tedbear.domain.sentence.entity.Sentence;
import com.ssafy.tedbear.domain.sentence.repository.SentenceRepository;
import com.ssafy.tedbear.domain.word.entity.Word;
import com.ssafy.tedbear.domain.word.entity.WordSentence;
import com.ssafy.tedbear.domain.word.repository.WordRepository;
import com.ssafy.tedbear.domain.word.repository.WordSentenceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GameServiceImpl implements GameService {
	private final WordRepository wordRepository;
	private final SentenceRepository sentenceRepository;
	private final MemberService memberService;
	private final GameRecordRepository gameRecordRepository;
	private final WordSentenceRepository wordSentenceRepository;

	@Override
	public WordGameDto getQuestion(Long memberId) {
		Word randomWord = wordRepository.findNoByRand();
		Member member = memberService.getMember(memberId);

		Optional<Sentence> sentence;
		do {
			sentence = sentenceRepository.findByWordOrderByMemberScore(randomWord.getNo(), member.getScore());
		} while (!sentence.isPresent());

		return new WordGameDto(randomWord, sentence.get());
	}

	@Override
	public void completeWordGame(long memberId, WordGameResultDto wordGameResultDto) {
		gameRecordRepository.save(wordGameResultDto.toEntity(memberId));
	}

	public CrossWordDto getCrossWord() {
		List<Word> wordList = wordRepository
			.findWordsForCrosswordGame()
			.stream()
			.sorted(Comparator.comparing(x -> x.getContent().length()))
			.collect(Collectors.toList());

		// 게임판의 크기
		int SIZE = 30;
		// (matrix가) 비어있음을 나타내는 상수
		char EMPTY_MATRIX = '.';
		// (힌트판이) 비어있음을 나타내는 상수
		int EMPTY = -1;
		// 힌트가 있는 칸이 아닌, 일반 문다가 들어있음을 나타내는 상수
		int BOX = 0;
		// 힌트가 나타내는 단어의 머리말임을 나타내는 상수
		int CLUEIDX = 1;

		// 실제 단어들이 들어갈 2차원 배열
		char[][] matrix = new char[SIZE][SIZE];
		// 힌트 번호, 문자가 들어있음을 나타낼 2차원 배열
		int[][] board = new int[SIZE][SIZE];

		for (int i = 0; i < SIZE; i++) {
			for (int j = 0; j < SIZE; j++) {
				matrix[i][j] = EMPTY_MATRIX;
				board[i][j] = EMPTY;
			}
		}
		List<GridDto> gridList = new ArrayList<>();
		List<ClueDto> clueList = new ArrayList<>();

		// 첫번째 단어 가로로 배치
		Word firstWord = wordList.remove(wordList.size() - 1);
		String firstWordContent = firstWord.getContent();
		int firstX = (SIZE - firstWordContent.length()) / 2;
		int firstY = SIZE / 2;
		Direction firstDirection = Direction.ACROSS;
		putWord(firstWord, firstY, firstX, firstDirection, CLUEIDX, matrix, board);
		clueList.add(getClueDto(firstWord, CLUEIDX, firstDirection, firstY, firstX, SIZE));
		CLUEIDX++;

		while (wordList.size() > 0) {
			Word word = wordList.remove(wordList.size() - 1);

			int canI = -1;
			int canJ = -1;
			Direction canDirection = null;
			boolean canPut = false;

			for (int i = 0; i < SIZE; i++) {
				for (int j = 0; j < SIZE; j++) {
					if (tryPutWord(word, i, j, matrix, board, Direction.DOWN)) {
						canI = i;
						canJ = j;
						canDirection = Direction.DOWN;
						canPut = true;
					}
					if (tryPutWord(word, i, j, matrix, board, Direction.ACROSS)) {
						canI = i;
						canJ = j;
						canDirection = Direction.ACROSS;
						canPut = true;
					}
				}
			}
			if (canPut) {
				putWord(word, canI, canJ, canDirection, CLUEIDX, matrix, board);
				clueList.add(getClueDto(word, CLUEIDX, canDirection, canI, canJ, SIZE));
				CLUEIDX++;
			}
		}
		printState(matrix, board);
		return new CrossWordDto(gridList, clueList, 4);
	}

	private ClueDto getClueDto(Word word, int clueIdx, Direction direction, int y, int x, int size) {
		Optional<WordSentence> wordSentence = wordSentenceRepository.findTop1ByWord(word);
		if (wordSentence.isPresent())
			return new ClueDto(word, wordSentence.get().getSentence(), clueIdx, size * y + x, direction.toString());
		return new ClueDto(word, clueIdx, size * y + x, direction.toString());
	}

	private void putWord(Word word, int y, int x, Direction direction, int clueIdx, char[][] matrix,
		int[][] board) {
		String content = word.getContent();
		if (direction == Direction.ACROSS) {
			for (int i = 0; i < content.length(); i++) {
				matrix[y][x + i] = content.charAt(i);
				board[y][x + i] = 0;
			}
		} else if (direction == Direction.DOWN) {
			for (int i = 0; i < content.length(); i++) {
				matrix[y + i][x] = content.charAt(i);
				board[y + i][x] = 0;
			}
		}
		board[y][x] = clueIdx;
	}

	private void printState(char[][] matrix, int[][] board) {
		System.out.println("====================================================");
		System.out.println();
		int SIZE = matrix.length;
		for (int i = 0; i < SIZE; i++) {
			System.out.println(Arrays.toString(matrix[i]));

			// for (int j = 0; j < SIZE; j++) {
			// 	System.out.print(matrix[i][j]);
			// }
			// System.out.println();
		}
		// System.out.println();
		// for (int i = 0; i < SIZE; i++) {
		// 	System.out.println(Arrays.toString(board[i]));
		// }
		System.out.println();
		System.out.println("====================================================");

	}

	private boolean tryPutWord(Word word, int i, int j, char[][] matrix, int[][] board, Direction direction) {
		String content = word.getContent();
		int len = content.length();
		int SIZE = matrix.length;

		if (direction == Direction.ACROSS) {
			if (j + len >= SIZE)
				return false;
			List<Integer> containsPos = new ArrayList<>();
			for (int k = 0; k < len; k++) {
				if (matrix[i][j + k] == '.')
					continue;
				// 빈칸이 아닌데, 다른 단어랑 겹치지 않는 경우 false
				if (matrix[i][j + k] != content.charAt(k))
					return false;
				containsPos.add(j + k);
			}

			// 겹치는 단어가 없을경우 false
			if (containsPos.isEmpty())
				return false;

			// 겹치는 지점이 양옆으로 인접할경우 false
			for (int a = 0; a < containsPos.size() - 1; a++)
				for (int b = a + 1; b < containsPos.size(); b++)
					if (Math.abs(containsPos.get(a) - containsPos.get(b)) == 1)
						return false;

			//배치할 단어의 양 끝에 단어가 있는 경우 false
			if ((j != 0 && matrix[i][j - 1] != '.') || (j + len < SIZE && matrix[i][j + len] != '.'))
				return false;

			// (가로로 배치할)단어의 위,아래가 비어있지 않은 경우 false (단, containsPos에 있는 좌표는 계산하지 않음)
			for (int k = 0; k < len; k++) {
				if (containsPos.contains(j + k))
					continue;
				if ((i > 0 && matrix[i - 1][j + k] != '.') || (i + 1 < SIZE && matrix[i + 1][j + k] != '.')) {
					return false;
				}
			}

			return true;
		} else {
			if (i + len >= SIZE)
				return false;
			List<Integer> containsPos = new ArrayList<>();
			for (int k = 0; k < len; k++) {
				if (matrix[i + k][j] == '.')
					continue;
				// 빈칸이 아닌데, 다른 단어랑 겹치지 않는 경우 false
				if (matrix[i + k][j] != content.charAt(k))
					return false;
				containsPos.add(i + k);
			}

			// 겹치는 단어가 없을경우 false
			if (containsPos.isEmpty())
				return false;

			// 겹치는 지점이 양옆으로 인접할경우 false
			for (int a = 0; a < containsPos.size() - 1; a++)
				for (int b = a + 1; b < containsPos.size(); b++)
					if (Math.abs(containsPos.get(a) - containsPos.get(b)) == 1)
						return false;

			//배치할 단어의 양 끝에 단어가 있는 경우 false
			if ((i != 0 && matrix[i - 1][j] != '.') || (i + len < SIZE && matrix[i + len][j] != '.'))
				return false;

			// (세로로 배치할)단어의 양옆이 비어있지 않은 경우 false (단, containsPos에 있는 좌표는 계산하지 않음)
			for (int k = 0; k < len; k++) {
				if (containsPos.contains(i + k))
					continue;
				if ((j > 0 && matrix[i + k][j - 1] != '.') || (j + 1 < SIZE && matrix[i + k][j + 1] != '.')) {
					return false;
				}
			}
			return true;
		}
	}
}